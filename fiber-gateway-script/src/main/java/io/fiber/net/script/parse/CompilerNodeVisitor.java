package io.fiber.net.script.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import io.fiber.net.script.Code;
import io.fiber.net.script.Library;
import io.fiber.net.script.Vm;
import io.fiber.net.script.ast.*;

import java.util.*;

public class CompilerNodeVisitor implements NodeVisitor<Void> {

    private static class Scope {
        Scope outer;
        Map<String, Integer> varIdx = new HashMap<>();
        final int outVarIdx;

        boolean isTryScope;

        int iteratePc;
        List<Integer> breakJumpPc;

        public Scope(Scope outer, int outVarIdx) {
            this.outer = outer;
            this.outVarIdx = outVarIdx;
        }

        void addBreakJump(int pc) {
            if (breakJumpPc == null) {
                breakJumpPc = new ArrayList<>();
            }
            breakJumpPc.add(pc);
        }

    }


    private int[] codes;
    private long[] positions;
    private int len;
    private Object[] operands;
    private int operandsLen;
    private int maxStack;
    private int currentStackSize;
    private int varLength;
    private int varCapacity;
    private final Map<Object, Integer> extCache = new HashMap<>();
    private final Stack<Scope> scopes = new Stack<>();

    private Scope getCurrentScope() {
        return scopes.peek();
    }

    private Scope enterScope() {
        Scope c = new Scope(scopes.empty() ? null : scopes.peek(), varLength);
        scopes.add(c);
        return c;
    }

    private void exitScope() {
        Scope pop = scopes.pop();
        varLength = pop.outVarIdx;
        if (pop.breakJumpPc != null) {
            for (Integer i : pop.breakJumpPc) {
                patchJumpPC(i);
            }
        }
    }

    private int defVar(String name) {
        Scope scope = getCurrentScope();
        if (scope.varIdx.containsKey(name)) {
            throw new ParseException("variable exists: " + name);
        }
        int idx = varLength++;
        scope.varIdx.put(name, idx);
        varCapacity = Integer.max(varCapacity, varLength);
        return idx;
    }

    private int getVar(String name) {
        Scope scope = getCurrentScope();
        Integer idx;
        while ((idx = scope.varIdx.get(name)) == null) {
            if ((scope = scope.outer) == null) {
                throw new ParseException("variable not exists: " + name);
            }
        }
        return idx;
    }


    private void push(int code, long position, int stackChange) {
        if (len == 0) {
            codes = new int[32];
            positions = new long[32];
        }
        if (codes.length <= len) {
            codes = Arrays.copyOf(codes, codes.length << 1);
            positions = Arrays.copyOf(positions, positions.length << 1);
        }

        codes[len] = code;
        positions[len] = position;
        len++;
        currentStackSize += stackChange;
        maxStack = Integer.max(maxStack, currentStackSize);
    }

    private void pushLoadConst(int position, JsonNode ct) {
        push((pushExt(ct.deepCopy()) << 8) | Code.LOAD_CONST, position, 1);
    }

    private int pushExt(Object ct) {
        if (ct instanceof Library.Function || ct instanceof Library.Constant || ct instanceof ValueNode || ct instanceof String) {
            Integer i = extCache.get(ct);
            if (i != null) {
                return i;
            }
        }

        int operandsLen = this.operandsLen;
        if (operands == null) {
            operands = new Object[16];
        } else if (operands.length <= operandsLen) {
            operands = Arrays.copyOf(operands, operands.length << 1);
        }
        operands[operandsLen] = ct;
        this.operandsLen++;
        extCache.put(ct, operandsLen);
        return operandsLen;
    }

    private int pushFalseJump(long pos) {
        push(Code.JUMP_IF_FALSE, pos, -1);
        return len;
    }

    private int pushRelationalJump(int code, long pos) {
        return pushRelationalJump(code, pos, 0);
    }

    private int pushRelationalJump(int code, long pos, int stackChange) {
        push(code, pos, stackChange);
        return len;
    }

    private void pushSpreadFunc(long pos, Library.Function func) {
        push((pushExt(func) << 8) | Code.CALL_FUNC_SPREAD, pos, 0);
    }

    private void patchJumpPC(int pc) {
        codes[pc - 1] |= len << 8;
    }

    @Override
    public Void visit(BinaryOperator binaryOperator) {
        binaryOperator.getLeft().accept(this);
        binaryOperator.getRight().accept(this);
        switch (binaryOperator.getOperator()) {
            case ADD:
                push(Code.BOP_PLUS, binaryOperator.getPos(), -1);
                break;
            case MINUS:
                push(Code.BOP_MINUS, binaryOperator.getPos(), -1);
                break;
            case MULTIPLY:
                push(Code.BOP_MULTIPLY, binaryOperator.getPos(), -1);
                break;
            case DIVIDE:
                push(Code.BOP_DIVIDE, binaryOperator.getPos(), -1);
                break;
            case MODULO:
                push(Code.BOP_MOD, binaryOperator.getPos(), -1);
                break;
            case MATCH:
                push(Code.BOP_MATCH, binaryOperator.getPos(), -1);
                break;
            case LT:
                push(Code.BOP_LT, binaryOperator.getPos(), -1);
                break;
            case GT:
                push(Code.BOP_GT, binaryOperator.getPos(), -1);
                break;

            case LTE:
                push(Code.BOP_LTE, binaryOperator.getPos(), -1);
                break;
            case GTE:
                push(Code.BOP_GTE, binaryOperator.getPos(), -1);
                break;
            case EQ:
                push(Code.BOP_EQ, binaryOperator.getPos(), -1);
                break;
            case SEQ:
                push(Code.BOP_SEQ, binaryOperator.getPos(), -1);
                break;
            case NE:
                push(Code.BOP_NE, binaryOperator.getPos(), -1);
                break;
            case SNE:
                push(Code.BOP_SNE, binaryOperator.getPos(), -1);
                break;
            default:
                throw new ParseException("变量只支持 $");
        }

        return null;
    }

    @Override
    public Void visit(Ternary node) {
        node.getTestVal().accept(this);
        int i = pushFalseJump(node.getPos());
        node.getTrueVal().accept(this);
        patchJumpPC(i);
        node.getFalseVal().accept(this);
        return null;
    }

    @Override
    public Void visit(LogicRelationalExpression exp) {
        exp.getLeft().accept(this);
        int i = pushRelationalJump(exp.getOperator() == Operator.AND ? Code.LOGICAL_AND : Code.LOGICAL_OR, exp.getPos());
        exp.getRight().accept(this);
        patchJumpPC(i);
        return null;
    }

    @Override
    public Void visit(Assign assign) {
        MaybeLValue left = assign.getLeft();
        assert left.isLValue() : "必须是左值";
        left.accept(this);
        int code, stackChange;
        if (left instanceof Indexer) {
            code = Code.IDX_SET;
            stackChange = -2;
        } else if (left instanceof VariableReference) {
            int i = getVar(((VariableReference) left).getName());
            code = Code.STORE_VAR_1 | (i << 8);
            stackChange = 0;
        } else {
            assert left instanceof PropertyReference;
            int i = pushExt(((PropertyReference) left).getName());
            code = Code.PROP_SET | (i << 8);
            stackChange = -1;
        }
        assign.getRight().accept(this);
        push(code, assign.getPos(), stackChange);
        return null;
    }

    @Override
    public Void visit(Indexer indexer) {
        indexer.getParent().accept(this);
        indexer.getKey().accept(this);
        if (!indexer.isLValue()) {
            push(Code.IDX_GET, indexer.getPos(), -1);
        }

        return null;
    }

    @Override
    public Void visit(Literal literal) {
        pushLoadConst(literal.getPos(), literal.getLiteralValue());
        return null;
    }

    @Override
    public Void visit(ConstantVal constantVal) {
        push((pushExt(constantVal.getConstant()) << 8) | Code.CALL_CONST, constantVal.getPos(), 1);
        return null;
    }

    @Override
    public Void visit(VariableReference variableReference) {
        if (variableReference.isRoot()) {
            if (variableReference.isLValue()) {
                throw new ParseException("$ 不能赋值");
            }
            push(Code.LOAD_ROOT, variableReference.getPos(), 1);
            return null;
        }

        int i = getVar(variableReference.getName());
        if (!variableReference.isLValue()) {
            push(Code.LOAD_VAR | (i << 8), variableReference.getPos(), 1);
        }
        return null;
    }

    @Override
    public Void visit(FunctionCall functionCall) {

        boolean spread = false;
        ExpressionNode[] args = functionCall.getArgs();
        for (ExpressionNode arg : args) {
            if (arg instanceof ExpandArrArg) {
                spread = true;
                break;
            }
        }
        if (spread) {
            push(Code.NEW_ARRAY, functionCall.getPos(), 1);
            for (ExpressionNode arg : args) {
                arg.accept(this);
                if (arg instanceof ExpandArrArg) {
                    push(Code.EXP_ARRAY, arg.getPos(), -1);
                } else {
                    push(Code.PUSH_ARRAY, arg.getPos(), -1);
                }
            }
            pushSpreadFunc(functionCall.getPos(), functionCall.getFunc());
        } else {
            if (args.length > 255) {
                throw new ParseException("参数过多：" + functionCall.getName());
            }
            for (ExpressionNode arg : args) {
                arg.accept(this);
            }
            push((pushExt(functionCall.getFunc()) << 16) | args.length << 8 | Code.CALL_FUNC, functionCall.getPos(), -args.length + 1);
        }
        return null;
    }

    @Override
    public Void visit(UnaryOperator unaryOperator) {
        unaryOperator.getTarget().accept(this);
        switch (unaryOperator.getOperator()) {
            case AND:
                push(Code.UNARY_PLUS, unaryOperator.getPos(), 0);
                break;
            case MINUS:
                push(Code.UNARY_MINUS, unaryOperator.getPos(), 0);
                break;
            case NOT:
                push(Code.UNARY_NEG, unaryOperator.getPos(), 0);
                break;
            case TYPEOF:
                push(Code.UNARY_TYPEOF, unaryOperator.getPos(), 0);
                break;
            default:
                throw new ParseException("unsupported unaryOperator" + unaryOperator.getOperator());
        }
        return null;
    }

    @Override
    public Void visit(ExpandArrArg expandArrArg) {
        expandArrArg.getOperand().accept(this);
        return null;
    }

    @Override
    public Void visit(PropertyReference propertyReference) {
        propertyReference.getTarget().accept(this);
        if (!propertyReference.isLValue()) {
            push((pushExt(propertyReference.getName()) << 8) | Code.PROP_GET, propertyReference.getPos(), 0);
        }
        return null;
    }

    @Override
    public Void visit(InlineList inlineList) {
        push(Code.NEW_ARRAY, inlineList.getPos(), 1);
        for (ExpressionNode child : inlineList.getChildren()) {
            child.accept(this);
            if (child instanceof ExpandArrArg) {
                push(Code.EXP_ARRAY, child.getPos(), -1);
            } else {
                push(Code.PUSH_ARRAY, child.getPos(), -1);
            }
        }
        return null;
    }

    @Override
    public Void visit(InlineObject inlineObject) {
        push(Code.NEW_OBJECT, inlineObject.getPos(), 1);
        Object[] keys = inlineObject.getKeys();
        ExpressionNode[] valueChildren = inlineObject.getValueChildren();
        for (int i = 0; i < keys.length; i++) {
            valueChildren[i].accept(this);
            if (InlineObject.isExpandKey(keys[i])) {
                push(Code.EXP_OBJECT, valueChildren[i].getPos(), -1);
            } else {
                assert keys[i] instanceof String : "key 必须是 string";
                push((pushExt(keys[i]) << 8) | Code.PROP_SET_1, valueChildren[i].getPos(), -1);
            }
        }
        return null;
    }

    @Override
    public Void visit(Block block) {
        enterScope();
        try {
            for (Statement statement : block.getStatements()) {
                statement.accept(this);
            }
            return null;
        } finally {
            exitScope();
        }
    }

    @Override
    public Void visit(IfStatement ifStatement) {
        ifStatement.getPredict().accept(this);
        int i = pushFalseJump(ifStatement.getPos());
        ifStatement.getTrueBlock().accept(this);
        patchJumpPC(i);
        if (ifStatement.getElseStatement() != null) {
            ifStatement.getElseStatement().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ForeachStatement foreachStatement) {

        foreachStatement.getCollection().accept(this);

        int start = pushRelationalJump(Code.INTO_ITERATE, foreachStatement.getPos(), -1);
        Scope scope = enterScope();
        scope.iteratePc = start;
        try {
            int k = defVar(foreachStatement.getKeyVarName().getName());
            int v = defVar(foreachStatement.getValVarName().getName());
            scope.addBreakJump(pushRelationalJump(Code.NEXT_ITERATE, foreachStatement.getPos(), 2)); // jump to end

            // assign key value;
            push(Code.STORE_VAR | (v << 8), foreachStatement.getValVarName().getPos(), -1);
            push(Code.STORE_VAR | (k << 8), foreachStatement.getKeyVarName().getPos(), -1);

            for (Statement statement : foreachStatement.getIterableBlock().getStatements()) {
                statement.accept(this);
            }
            push(Code.JUMP | (start << 8), foreachStatement.getPos(), 2); // jump to end

        } finally {
            exitScope();
        }
        //
        push(Code.END_ITERATE, foreachStatement.getKeyVarName().getPos(), 0);
        return null;
    }

    @Override
    public Void visit(VariableDeclareStatement variableDeclareStatement) {
        Identifier variableName = variableDeclareStatement.getVariableName();
        int i = defVar(variableName.getName());
        ExpressionNode initialExp = variableDeclareStatement.getInitialExp();
        if (initialExp != null) {
            initialExp.accept(this);
        } else {
            pushLoadConst(variableDeclareStatement.getPos(), NullNode.getInstance());
        }

        push(Code.STORE_VAR | (i << 8), variableDeclareStatement.getPos(), -1);
        return null;
    }

    @Override
    public Void visit(ExpressionStatement expressionStatement) {
        ExpressionNode expression = expressionStatement.getExpression();
        expression.accept(this);
        push(Code.POP, expressionStatement.getPos(), -1);
        return null;
    }

    @Override
    public Void visit(ContinueStatement breakStatement) {
        Scope scope = getCurrentScope();
        while (scope != null && scope.iteratePc == 0) {
            if (scope.isTryScope) {
                push(Code.END_TRY, breakStatement.getPos(), 0);
            }
            scope = scope.outer;
        }
        if (scope == null) {
            throw new ParseException("continue statement not in foreach");
        }

        push(Code.JUMP | (scope.iteratePc << 8), breakStatement.getPos(), 0);
        return null;
    }

    @Override
    public Void visit(BreakStatement breakStatement) {
        Scope scope = getCurrentScope();
        while (scope != null && scope.iteratePc == 0) {
            if (scope.isTryScope) {
                push(Code.END_TRY, breakStatement.getPos(), 0);
            }
            scope = scope.outer;
        }
        if (scope == null) {
            throw new ParseException("break statement not in foreach");
        }

        scope.addBreakJump(pushRelationalJump(Code.JUMP, breakStatement.getPos()));
        return null;
    }

    @Override
    public Void visit(ReturnStatement returnStatement) {
        ExpressionNode expression = returnStatement.getExpression();
        if (expression != null) {
            expression.accept(this);
        }
        push(Code.END_RETURN, returnStatement.getPos(), 0);
        return null;
    }

    @Override
    public Void visit(NoopNode noopNode) {
        push(Code.NOOP, 0, 0);
        return null;
    }

    @Override
    public Void visit(TryCatchStatement tryCatchStatement) {
        Statement tryBlock = tryCatchStatement.getTryBlock();
        Statement catchBlock = tryCatchStatement.getCatchBlock();
        Identifier expVarName = tryCatchStatement.getExpVarName();
        int patchPc = pushRelationalJump(Code.INTO_TRY, tryBlock.getPos(), 0);
        Scope scope = enterScope();
        scope.isTryScope = true;
        if (tryBlock instanceof NoopNode) {
            exitScope();
            return null;
        } else if (tryBlock instanceof Block) {
            for (Statement statement : ((Block) tryBlock).getStatements()) {
                statement.accept(this);
            }
        } else {
            tryBlock.accept(this);
        }

        push(Code.END_TRY, tryBlock.getPos(), 0);
        exitScope();
        int patchJump = pushRelationalJump(Code.JUMP, tryBlock.getPos(), 0);
        patchJumpPC(patchPc);
        enterScope();
        push(Code.INTO_CATCH, expVarName.getPos(), 1);
        int i = defVar(expVarName.getName());
        push(Code.STORE_VAR | (i << 8), expVarName.getPos(), -1);
        if (catchBlock instanceof Block) {
            for (Statement statement : ((Block) catchBlock).getStatements()) {
                statement.accept(this);
            }
        } else {
            catchBlock.accept(this);
        }
        exitScope();
        patchJumpPC(patchJump);
        return null;
    }

    @Override
    public Void visit(ThrowStatement throwStatement) {
        ExpressionNode expressionNode = throwStatement.getExpressionNode();
        expressionNode.accept(this);
        push(Code.THROW_EXP, throwStatement.getPos(), -1);
        return null;
    }


    static class Compiled {
        private int stackSize;
        private int varTableSize;
        private long[] pos;
        private int[] codes;
        private Object[] operands;


        Vm createVM(JsonNode root, Object attach) {
            return new Vm(codes, operands, stackSize, varTableSize, root, attach);
        }

        public long[] getPos() {
            return pos;
        }
    }

    static Compiled compile(Node node) {
        CompilerNodeVisitor nodeVisitor = new CompilerNodeVisitor();
        node.accept(nodeVisitor);

        Compiled compiled = new Compiled();
        compiled.stackSize = nodeVisitor.maxStack;
        compiled.varTableSize = nodeVisitor.varCapacity;
        compiled.pos = Arrays.copyOf(nodeVisitor.positions, nodeVisitor.len);
        compiled.codes = Arrays.copyOf(nodeVisitor.codes, nodeVisitor.len);
        if (nodeVisitor.operandsLen > 0) {
            compiled.operands = Arrays.copyOf(nodeVisitor.operands, nodeVisitor.operandsLen);
        }
        return compiled;
    }

}
