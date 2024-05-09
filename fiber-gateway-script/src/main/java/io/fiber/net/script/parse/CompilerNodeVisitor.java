package io.fiber.net.script.parse;

import io.fiber.net.common.json.ContainerNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.*;
import io.fiber.net.script.run.Code;
import io.fiber.net.script.run.InterpreterVm;

import java.util.*;

public class CompilerNodeVisitor implements NodeVisitor<Void> {
    private static final int SCOPE_TYPE_SCRIPT = 0;
    private static final int SCOPE_TYPE_TRY = 1;
    private static final int SCOPE_TYPE_CATCH = 2;
    private static final int SCOPE_TYPE_FOR = 3;
    private static final int SCOPE_TYPE_IF = 4;
    private static final int SCOPE_TYPE_ELSE = 5;

    private static final int TERMINAL_TYPE_NONE = 0;
    private static final int TERMINAL_BIT_ITERATOR = 1;
    private static final int TERMINAL_BIT_SCRIPT = 2;
    private static final int TERMINAL_TYPE_ITERATOR = TERMINAL_BIT_ITERATOR;
    private static final int TERMINAL_TYPE_SCRIPT = TERMINAL_BIT_ITERATOR | TERMINAL_BIT_SCRIPT;


    private static class Scope {
        Scope outer;
        Map<String, Integer> varIdx = new HashMap<>();
        final int outVarIdx;
        final int type;
        final int codeIdx;

        int iteratePc;
        List<Integer> breakJumpPc;
        int terminal = TERMINAL_TYPE_NONE;

        public Scope(Scope outer, int outVarIdx, int type, int codeIdx) {
            this.outer = outer;
            this.outVarIdx = outVarIdx;
            this.type = type;
            this.codeIdx = codeIdx;
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
    private int iteratorVarIdx;
    private int[] expMapping;
    private int emLen;

    private final Map<Object, Integer> extCache = new HashMap<>();
    private final Map<Object, Integer> extIdtCache = new IdentityHashMap<>();
    private final Stack<Scope> scopes = new Stack<>();

    private Scope getCurrentScope() {
        return scopes.peek();
    }

    private int addExpMapping(int cpc) {
        int el = emLen;
        if (el == 0) {
            expMapping = new int[24];
        } else if (expMapping.length <= el) {
            expMapping = Arrays.copyOf(expMapping, expMapping.length << 1);
        }
        expMapping[el] = cpc;

        emLen = el + 3;
        return el;
    }

    private void patchExpMapping(int el, int catchBegin, int catchEnd) {
        expMapping[el + 1] = catchBegin;
        expMapping[el + 2] = catchEnd;
    }

    private Scope enterScope(int type) {
        Scope c = new Scope(scopes.empty() ? null : scopes.peek(), varLength, type, len);
        scopes.add(c);
        return c;
    }

    private int exitScope() {
        Scope pop = scopes.pop();
        varLength = pop.outVarIdx;
        if (pop.breakJumpPc != null) {
            for (Integer i : pop.breakJumpPc) {
                patchJumpPC(i);
            }
        }
        return pop.terminal;
    }

    private void checkDeadCode(int pos) {
        if (getCurrentScope().terminal != TERMINAL_TYPE_NONE) {
            throw new ParseException("dead code is not allowed: @" + AstUtils.startPos(pos));
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

    private int defIterateVar(String name) {
        if ("_".equals(name)) {
            return -1;
        }
        return defVar(name);
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
            codes = new int[64];
            positions = new long[64];
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
        int id;
        if (ct instanceof ValueNode
                || ct instanceof String) {
            Integer i = extCache.get(ct);
            if (i != null) {
                return i;
            }
            id = pushExt0(ct);
            extCache.put(ct, id);
        } else if (ct instanceof Library.Function
                || ct instanceof Library.Constant
                || ct instanceof Library.AsyncFunction
                || ct instanceof Library.AsyncConstant) {
            Integer i = extIdtCache.get(ct);
            if (i != null) {
                return i;
            }
            id = pushExt0(ct);
            extIdtCache.put(ct, id);
        } else {
            id = pushExt0(ct);
        }

        return id;
    }

    private int pushExt0(Object ct) {
        int operandsLen = this.operandsLen;
        if (operands == null) {
            operands = new Object[16];
        } else if (operands.length <= operandsLen) {
            operands = Arrays.copyOf(operands, operands.length << 1);
        }
        operands[operandsLen] = ct;
        this.operandsLen++;
        return operandsLen;
    }

    private int pushLogicJump(int code, long pos) {
        push(code, pos, -1);
        return len;
    }

    private int pushJump(long pos) {
        push(Code.JUMP, pos, 0);
        return len;
    }

    private void pushJump(int pc, long pos) {
        push(Code.JUMP | (pc << 8), pos, 0);
    }

    private int getCpc() {
        return len;
    }

    private void pushSpreadFunc(long pos, Library.Function func) {
        push((pushExt(func) << 8) | Code.CALL_FUNC_SPREAD, pos, 0);
    }

    private void pushSpreadAsyncFunc(long pos, Library.AsyncFunction func) {
        push((pushExt(func) << 8) | Code.CALL_ASYNC_FUNC_SPREAD, pos, 0);
    }

    private void pushFunction(long pos, Library.Function func, int argCount) {
        push((pushExt(func) << 16) | (argCount << 8) | Code.CALL_FUNC, pos, -argCount + 1);
    }

    private void pushAsyncFunction(long pos, Library.AsyncFunction func, int argCount) {
        push((pushExt(func) << 16) | (argCount << 8) | Code.CALL_ASYNC_FUNC, pos, -argCount + 1);
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
            case IN:
                push(Code.BOP_IN, binaryOperator.getPos(), -1);
                break;
            default:
                throw new ParseException("unsupported operator");
        }

        return null;
    }

    @Override
    public Void visit(Ternary node) {
        node.getTestVal().accept(this);
        int i = pushLogicJump(Code.JUMP_IF_FALSE, node.getPos());
        node.getTrueVal().accept(this);
        int eJump = pushJump(node.getTrueVal().getPos());
        patchJumpPC(i);
        node.getFalseVal().accept(this);
        patchJumpPC(eJump);
        return null;
    }

    @Override
    public Void visit(LogicRelationalExpression exp) {
        exp.getLeft().accept(this);
        push(Code.DUMP, exp.getLeft().getPos(), 1);
        int i = pushLogicJump(exp.getOperator() == Operator.AND ? Code.JUMP_IF_FALSE : Code.JUMP_IF_TRUE,
                exp.getPos());
        push(Code.POP, exp.getRight().getPos(), -1);
        exp.getRight().accept(this);
        patchJumpPC(i);
        return null;
    }

    @Override
    public Void visit(Assign assign) {
        visitAssign(assign, true);
        return null;
    }

    private boolean visitAssign(Assign assign, boolean requireResult) {
        boolean noResult = false;
        MaybeLValue left = assign.getLeft();
        assert left.isLValue() : "必须是左值";
        left.accept(this);
        int code, stackChange;
        if (left instanceof Indexer) {
            code = Code.IDX_SET;
            stackChange = -2;
        } else if (left instanceof VariableReference) {
            int i = getVar(((VariableReference) left).getName());
            code = Code.STORE_VAR | (i << 8);
            stackChange = -1;
            noResult = true;
        } else {
            assert left instanceof PropertyReference;
            int i = pushExt(((PropertyReference) left).getName());
            code = Code.PROP_SET | (i << 8);
            stackChange = -1;
        }
        assign.getRight().accept(this);
        if (requireResult && noResult) {
            push(Code.DUMP, assign.getEndPosition(), 1);
        }
        push(code, assign.getPos(), stackChange);
        return noResult;
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
        if (constantVal.isAsync()) {
            push((pushExt(constantVal.getAsyncConstant()) << 8) | Code.CALL_ASYNC_CONST, constantVal.getPos(), 1);
        } else {
            push((pushExt(constantVal.getConstant()) << 8) | Code.CALL_CONST, constantVal.getPos(), 1);
        }

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
                if (!(arg instanceof ExpandArrArg)) {
                    push(Code.PUSH_ARRAY, arg.getPos(), -1);
                }
            }
            if (functionCall.isAsync()) {
                pushSpreadAsyncFunc(functionCall.getPos(), functionCall.getAsyncFunc());
            } else {
                pushSpreadFunc(functionCall.getPos(), functionCall.getFunc());
            }

        } else {
            if (args.length > 255) {
                throw new ParseException("参数过多：" + functionCall.getName());
            }
            for (ExpressionNode arg : args) {
                arg.accept(this);
            }

            if (functionCall.isAsync()) {
                pushAsyncFunction(functionCall.getPos(), functionCall.getAsyncFunc(), args.length);
            } else {
                pushFunction(functionCall.getPos(), functionCall.getFunc(), args.length);
            }
        }
        return null;
    }

    @Override
    public Void visit(UnaryOperator unaryOperator) {
        unaryOperator.getTarget().accept(this);
        switch (unaryOperator.getOperator()) {
            case ADD:
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
        switch (expandArrArg.getWhere()) {
            case INIT_ARR:
            case FUNC_CALL:
                push(Code.EXP_ARRAY, expandArrArg.getPos(), -1);
                break;
            case INIT_OBJ:
                push(Code.EXP_OBJECT, expandArrArg.getPos(), -1);
                break;
        }
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
            if (!(child instanceof ExpandArrArg)) {
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
            if (!InlineObject.isExpandKey(keys[i])) {
                assert keys[i] instanceof String : "key 必须是 string";
                push((pushExt(keys[i]) << 8) | Code.PROP_SET_1, valueChildren[i].getPos(), -1);
            }
        }
        return null;
    }

    private int lastIfBlockTerminal;

    @Override
    public Void visit(Block block) {
        int type;
        switch (block.getType()) {
            case SCRIPT:
                type = SCOPE_TYPE_SCRIPT;
                break;
            case IF:
                type = SCOPE_TYPE_IF;
                break;
            case ELSE:
                type = SCOPE_TYPE_ELSE;
                break;
            // 这个已经被 enter scope了 not hit
            case TRY:
            case CATCH:
            case FOR:
            default:
                throw new IllegalStateException("invalid scope type");
        }
        enterScope(type);
        try {
            for (Statement statement : block.getStatements()) {
                statement.accept(this);
            }
            return null;
        } finally {
            int t = exitScope();
            if (type == SCOPE_TYPE_SCRIPT) {
                if ((t & TERMINAL_BIT_SCRIPT) != 0 && !scopes.empty()) {
                    getCurrentScope().terminal |= TERMINAL_TYPE_SCRIPT;
                }
                if (scopes.empty() && (t & TERMINAL_BIT_SCRIPT) == 0) {
                    push(Code.END_RETURN, AstUtils.toPos(block.getEndPosition(), block.getEndPosition()), 0);
                }
            } else {
                lastIfBlockTerminal = t;
            }
        }
    }

    @Override
    public Void visit(IfStatement ifStatement) {
        checkDeadCode(ifStatement.getPos());
        ifStatement.getPredict().accept(this);
        int i = pushLogicJump(Code.JUMP_IF_FALSE, ifStatement.getPredict().getPos());
        Block trueBlock = ifStatement.getTrueBlock();
        Assert.isTrue(trueBlock.getType() == Block.Type.IF);
        trueBlock.accept(this);
        int trueTerminal = lastIfBlockTerminal;
        lastIfBlockTerminal = TERMINAL_TYPE_NONE;
        // predict
        // .... true  block
        //
        // label
        // .... false block
        // continue
        int jElse = -1;
        Block elseBlock = ifStatement.getElseBlock();
        if (elseBlock != null && (trueTerminal & TERMINAL_BIT_ITERATOR) == 0) {
            jElse = pushJump(trueBlock.getPos());
        }
        patchJumpPC(i);
        if (elseBlock != null) {
            Assert.isTrue(elseBlock.getType() == Block.Type.ELSE);
            elseBlock.accept(this);
            int falseTerminal = lastIfBlockTerminal;
            lastIfBlockTerminal = TERMINAL_TYPE_NONE;
            if (jElse >= 0) {
                patchJumpPC(jElse);
            }
            getCurrentScope().terminal |= TERMINAL_BIT_SCRIPT & trueTerminal & falseTerminal;
            getCurrentScope().terminal |= TERMINAL_BIT_ITERATOR & trueTerminal & falseTerminal;
        }
        return null;
    }

    private boolean mustHasIterate(ExpressionNode node) {
        if (!(node instanceof Literal)) {
            return false;
        }

        JsonNode literalValue = ((Literal) node).getLiteralValue();
        return literalValue instanceof ContainerNode && !literalValue.isEmpty();
    }

    @Override
    public Void visit(ForeachStatement foreachStatement) {
        checkDeadCode(foreachStatement.getPos());
        ExpressionNode collection = foreachStatement.getCollection();
        collection.accept(this);
        final Scope scope = enterScope(SCOPE_TYPE_FOR);
        int i = defVar("__iterator__var_" + (iteratorVarIdx++));
        if (i > InterpreterVm.MAX_ITERATOR_VAR) {
            throw new IllegalStateException("too many variables");
        }
        push(Code.ITERATE_INTO | (i << 8), foreachStatement.getPos(), -1);
        scope.iteratePc = getCpc();
        try {
            int k = defIterateVar(foreachStatement.getKeyVarName().getName());
            int v = defIterateVar(foreachStatement.getValVarName().getName());
            if (k >= InterpreterVm.MAX_ITERATOR_VAR || v >= InterpreterVm.MAX_ITERATOR_VAR) {
                throw new IllegalStateException("too many variables");
            }

            push(Code.ITERATE_NEXT | (i << InterpreterVm.INSTRUMENT_LEN), foreachStatement.getPos(), 1);
            scope.addBreakJump(pushLogicJump(Code.JUMP_IF_FALSE, foreachStatement.getPos())); // jump to end

            // assign key value;
            if (k >= 0) {
                push((i << InterpreterVm.ITERATOR_OFF) | (k << InterpreterVm.INSTRUMENT_LEN) | Code.ITERATE_KEY,
                        foreachStatement.getKeyVarName().getPos(), 0);
            }
            if (v >= 0) {
                push((i << InterpreterVm.ITERATOR_OFF) | (v << InterpreterVm.INSTRUMENT_LEN) | Code.ITERATE_VALUE,
                        foreachStatement.getValVarName().getPos(), 0);
            }
            for (Statement statement : foreachStatement.getIterableBlock().getStatements()) {
                statement.accept(this);
            }
            if (getCurrentScope().terminal == TERMINAL_TYPE_NONE) {
                push(Code.JUMP | (scope.iteratePc << 8), foreachStatement.getPos(), 0); // jump to end
            }
        } finally {
            if ((exitScope() & TERMINAL_BIT_SCRIPT) != 0 && mustHasIterate(collection)) {
                getCurrentScope().terminal |= TERMINAL_TYPE_SCRIPT;
            }
        }
        return null;
    }

    @Override
    public Void visit(VariableDeclareStatement variableDeclareStatement) {
        checkDeadCode(variableDeclareStatement.getPos());
        ExpressionNode initialExp = variableDeclareStatement.getInitialExp();
        if (initialExp != null) {
            initialExp.accept(this);
        } else {
            pushLoadConst(variableDeclareStatement.getPos(), MissingNode.getInstance());
        }
        Identifier variableName = variableDeclareStatement.getVariableName();
        int i = defVar(variableName.getName());
        push(Code.STORE_VAR | (i << 8), variableDeclareStatement.getPos(), -1);
        return null;
    }

    @Override
    public Void visit(ExpressionStatement expressionStatement) {
        checkDeadCode(expressionStatement.getPos());
        ExpressionNode expression = expressionStatement.getExpression();
        if (expression instanceof Assign) {
            if (!visitAssign((Assign) expression, false)) {
                push(Code.POP, expressionStatement.getPos(), -1);
            }
            return null;
        }

        expression.accept(this);
        push(Code.POP, expressionStatement.getPos(), -1);
        return null;
    }

    @Override
    public Void visit(ContinueStatement continueStatement) {
        checkDeadCode(continueStatement.getPos());
        Scope scope = getCurrentScope();
        scope.terminal |= TERMINAL_TYPE_ITERATOR;
        while (scope != null && scope.type != SCOPE_TYPE_FOR) {
            scope = scope.outer;
        }
        if (scope == null) {
            throw new ParseException("continue statement not in foreach");
        }

        pushJump(scope.iteratePc, continueStatement.getPos());
        return null;
    }

    @Override
    public Void visit(BreakStatement breakStatement) {
        checkDeadCode(breakStatement.getPos());
        Scope scope = getCurrentScope();
        scope.terminal |= TERMINAL_TYPE_ITERATOR;
        while (scope != null && scope.type != SCOPE_TYPE_FOR) {
            scope = scope.outer;
        }
        if (scope == null) {
            throw new ParseException("break statement not in foreach");
        }
        scope.addBreakJump(pushJump(breakStatement.getPos()));
        return null;
    }

    @Override
    public Void visit(ReturnStatement returnStatement) {
        checkDeadCode(returnStatement.getPos());
        ExpressionNode expression = returnStatement.getExpression();
        if (expression != null) {
            expression.accept(this);
        }
        push(Code.END_RETURN, returnStatement.getPos(), 0);
        getCurrentScope().terminal |= TERMINAL_TYPE_SCRIPT;
        return null;
    }

    @Override
    public Void visit(NoopNode noopNode) {
        checkDeadCode(noopNode.getPos());
        push(Code.NOOP, 0, 0);
        return null;
    }

    @Override
    public Void visit(TryCatchStatement tryCatchStatement) {
        checkDeadCode(tryCatchStatement.getPos());

        Statement tryBlock = tryCatchStatement.getTryBlock();
        Statement catchBlock = tryCatchStatement.getCatchBlock();
        Identifier expVarName = tryCatchStatement.getExpVarName();
        enterScope(SCOPE_TYPE_TRY);
        int patchPc = getCpc();
        if (tryBlock instanceof NoopNode) {
            exitScope();
            return null;
        }

        int tryPoint = addExpMapping(patchPc);

        if (tryBlock instanceof Block) {
            for (Statement statement : ((Block) tryBlock).getStatements()) {
                statement.accept(this);
            }
        } else {
            tryBlock.accept(this);
        }

        int tryTerminal = exitScope();
        int patchJump = -1;
        if (tryTerminal == TERMINAL_TYPE_NONE) {
            patchJump = pushJump(tryBlock.getPos());
        }
        enterScope(SCOPE_TYPE_CATCH);
        int i = defVar(expVarName.getName());
        int catchBegin = getCpc();
        push(Code.INTO_CATCH | (i << 8), expVarName.getPos(), 0);
        if (catchBlock instanceof Block) {
            for (Statement statement : ((Block) catchBlock).getStatements()) {
                statement.accept(this);
            }
        } else {
            catchBlock.accept(this);
        }
        int catchTerminal = exitScope();
        if (patchJump != -1) {
            patchJumpPC(patchJump);
        }
        getCurrentScope().terminal |= TERMINAL_BIT_SCRIPT & tryTerminal & catchTerminal;
        getCurrentScope().terminal |= TERMINAL_BIT_ITERATOR & tryTerminal & catchTerminal;
        patchExpMapping(tryPoint, catchBegin, getCpc());
        return null;
    }

    @Override
    public Void visit(ThrowStatement throwStatement) {
        checkDeadCode(throwStatement.getPos());
        ExpressionNode expressionNode = throwStatement.getExpressionNode();
        expressionNode.accept(this);
        push(Code.THROW_EXP, throwStatement.getPos(), -1);
        getCurrentScope().terminal |= TERMINAL_TYPE_SCRIPT;
        return null;
    }


    static Compiled compile(Node node) {
        CompilerNodeVisitor nodeVisitor = new CompilerNodeVisitor();
        node.accept(nodeVisitor);

        Compiled compiled = new Compiled(nodeVisitor.maxStack,
                nodeVisitor.varCapacity,
                Arrays.copyOf(nodeVisitor.positions, nodeVisitor.len),
                Arrays.copyOf(nodeVisitor.codes, nodeVisitor.len),
                nodeVisitor.operandsLen > 0 ? Arrays.copyOf(nodeVisitor.operands, nodeVisitor.operandsLen) : null
        );
        compiled.exceptionTable = nodeVisitor.emLen > 0 ? Arrays.copyOf(nodeVisitor.expMapping, nodeVisitor.emLen) : null;
        compiled.init();
        return compiled;
    }

    public static Compiled compileFromScript(String script, Library library) {
        Block block = new Parser(library, true).parseScript(script);
        return compile(block);
    }


}
