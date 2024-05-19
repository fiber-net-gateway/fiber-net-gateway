package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.Predictions;
import io.fiber.net.script.ast.*;
import io.fiber.net.script.run.Compares;
import io.fiber.net.script.run.InterpreterVm;

import java.util.*;
import java.util.stream.Collectors;

public class OptimiserNodeVisitor implements NodeVisitor<Node> {
    private static final NoopEmitter NOOP_EMITTER = new NoopEmitter();

    public static NoopEmitter noopEmitter() {
        return NOOP_EMITTER;
    }

    private static class NoopEmitter implements Maybe.Emitter<JsonNode> {

        @Override
        public void onSuccess(JsonNode jsonNode) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    static Node optimiseAst(Node node) {
        return node.accept(new OptimiserNodeVisitor());
    }

    private static class VarDef {
        private final String name;
        private JsonNode constLiteral;
        private boolean hasChanged;
        private int varRead;

        private VarDef(String name) {
            this.name = name;
        }

        public boolean isConst() {
            return !hasChanged && constLiteral instanceof ValueNode;
        }

        public String getName() {
            return name;
        }
    }

    private static class Scope {
        final Scope outer;
        Map<String, VarDef> varTable = new HashMap<>();

        private Scope(Scope outer) {
            this.outer = outer;
        }
    }

    private final Stack<Scope> scopes = new Stack<>();


    private Scope enterScope() {
        Scope c = new Scope(scopes.empty() ? null : scopes.peek());
        scopes.add(c);
        return c;
    }

    private Scope exitScope() {
        return scopes.pop();
    }

    private Scope getCurrentScope() {
        return scopes.peek();
    }

    private VarDef addVarDef(String name, ExpressionNode initialExp) {
        final Scope scope = getCurrentScope();
        if (scope.varTable.containsKey(name)) {
            throw new ParseException("variable exists:" + name);
        }
        VarDef varDef = new VarDef(name);
        if (initialExp instanceof Literal) {
            varDef.constLiteral = ((Literal) initialExp).getLiteralValue();
        }
        scope.varTable.put(name, varDef);
        return varDef;
    }


    private VarDef getVarDef(String name) {
        Scope scope = getCurrentScope();
        do {
            if (scope.varTable.containsKey(name)) {
                return scope.varTable.get(name);
            }
            scope = scope.outer;
        } while (scope != null);
        throw new ParseException("variable not exists:" + name);
    }

    private Literal optimise(ExpressionNode node) {
        if (node instanceof Literal) {
            return (Literal) node;
        }

        Block block = new Block(0, Collections.singletonList(new ReturnStatement(0, node)), Block.Type.SCRIPT);
        InterpreterVm vm = new InterpreterVm(CompilerNodeVisitor.compile(block), NullNode.getInstance(), null, NOOP_EMITTER);
        JsonNode jsonNode;
        try {
            vm.exec();
            jsonNode = vm.getResultNow();
        } catch (Throwable e) {
            StringBuilder sb = new StringBuilder();
            node.toStringAST(sb);
            throw new ParseException("optimise failed:" + sb, e);
        }
        StringBuilder sb = new StringBuilder();
        node.toStringAST(sb);
        return new Literal("<optimised:" + sb + ">", node.getPos(), jsonNode);
    }

    @Override
    public ExpressionNode visit(BinaryOperator node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        node.setLeft((ExpressionNode) node.getLeft().accept(this));
        node.setRight((ExpressionNode) node.getRight().accept(this));

        if (node.isConstant()) {
            return optimise(node);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(Ternary node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode test = (ExpressionNode) node.getTestVal().accept(this);
        ExpressionNode tr = (ExpressionNode) node.getTrueVal().accept(this);
        ExpressionNode fl = (ExpressionNode) node.getFalseVal().accept(this);
        if (test != node.getTestVal() || tr != node.getTrueVal() || fl != node.getFalseVal()) {
            return new Ternary(node.getPos(), test, tr, fl);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(LogicRelationalExpression node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode left = (ExpressionNode) node.getLeft().accept(this);
        ExpressionNode right = (ExpressionNode) node.getRight().accept(this);
        if (left != node.getLeft() || right != node.getRight()) {
            return new LogicRelationalExpression(node.getPos(), left, node.getOperator(), right);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(Assign node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode left = (ExpressionNode) node.getLeft().accept(this);
        ExpressionNode right = (ExpressionNode) node.getRight().accept(this);
        if (left != node.getLeft() || right != node.getRight()) {
            return new Assign(node.getPos(), (MaybeLValue) left, right);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(Indexer node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode parent = (ExpressionNode) node.getParent().accept(this);
        ExpressionNode key = (ExpressionNode) node.getKey().accept(this);
        if (parent != node.getParent() || key != node.getKey()) {
            return new Indexer(node.getPos(), parent, key);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(Literal literal) {
        return literal;
    }

    @Override
    public ExpressionNode visit(ConstantVal node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        return node;
    }

    @Override
    public ExpressionNode visit(VariableReference node) {
        if (node.isRoot()) {
            return node;
        }
        VarDef varDef = getVarDef(node.getName());
        if (node.isLValue()) {
            varDef.hasChanged = true;
        } else if (varDef.isConst()) {
            return new Literal("", node.getPos(), varDef.constLiteral);
        } else {
            varDef.varRead++;
        }
        return node;
    }

    @Override
    public ExpressionNode visit(FunctionCall node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode[] args = node.getArgs();
        for (int i = 0; i < args.length; i++) {
            args[i] = (ExpressionNode) args[i].accept(this);
        }
        if (node.isConstant()) {
            return optimise(node);
        }

        return node;
    }

    @Override
    public ExpressionNode visit(UnaryOperator node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode left = (ExpressionNode) node.getTarget().accept(this);
        if (left != node.getTarget()) {
            return new UnaryOperator(node.getPos(), node.getOperator(), left);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(ExpandArrArg node) {
        ExpressionNode left = (ExpressionNode) node.getOperand().accept(this);
        if (left != node.getOperand()) {
            return new ExpandArrArg(node.getPos(), left, node.getWhere());
        }
        return node;
    }

    @Override
    public ExpressionNode visit(PropertyReference node) {
        if (node.isConstant()) {
            return optimise(node);
        }

        ExpressionNode left = (ExpressionNode) node.getTarget().accept(this);
        if (left != node.getTarget()) {
            return new PropertyReference(node.getName(), node.getPos(), left);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(InlineList node) {
        if (node.isConstant()) {
            return optimise(node);
        }
        ExpressionNode[] children = node.getChildren();
        for (int i = 0; i < children.length; i++) {
            children[i] = (ExpressionNode) children[i].accept(this);
        }
        return node;
    }

    @Override
    public ExpressionNode visit(InlineObject node) {
        if (node.isConstant()) {
            return optimise(node);
        }
        ExpressionNode[] children = node.getValueChildren();
        for (int i = 0; i < children.length; i++) {
            children[i] = (ExpressionNode) children[i].accept(this);
        }
        return node;
    }

    @Override
    public Node visit(Block block) {
        enterScope();
        return visitBlockAndExistScope(block);

    }

    private Statement visitBlockAndExistScope(Block block) {

        List<Statement> statementList = new ArrayList<>();
        Scope scope;
        try {
            for (Statement statement : block.getStatements()) {
                Node node = statement.accept(this);
                if (node == NoopNode.INS) {
                    continue;
                }
                statementList.add((Statement) node);
            }
        } finally {
            scope = exitScope();
        }

        statementList = statementList.stream().map(statement -> {
            if (statement instanceof VariableDeclareStatement) {
                VariableDeclareStatement varDec = (VariableDeclareStatement) statement;
                if (scope.varTable.get(varDec.getVariableName().getName()).varRead == 0) {
                    if (varDec.getInitialExp() instanceof Literal) {
                        return null;
                    }
                    return new ExpressionStatement(statement.getPos(), varDec.getInitialExp());
                }
            }
            return statement;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (statementList.isEmpty()) {
            return NoopNode.INS;
        }
        return new Block(block.getPos(), statementList, block.getType());
    }

    @Override
    public Node visit(IfStatement ifStatement) {
        ExpressionNode predict = ifStatement.getPredict();
        ExpressionNode expressionNode = (ExpressionNode) predict.accept(this);
        Block trueBlock = ifStatement.getTrueBlock();
        Assert.isTrue(trueBlock.getType() == Block.Type.IF);
        Statement ts = (Statement) trueBlock.accept(this);
        Block elseBlock = ifStatement.getElseBlock();
        Statement es = elseBlock != null ? (Statement) elseBlock.accept(this) : null;
        if (expressionNode instanceof Literal) {
            if (Compares.logic(((Literal) expressionNode).getLiteralValue())) {
                if (ts instanceof Block) {
                    ((Block) ts).setType(Block.Type.SCRIPT);
                }
                return ts;
            } else if (es != null) {
                if (es instanceof Block) {
                    ((Block) es).setType(Block.Type.SCRIPT);
                }
                return es;
            } else {
                return NoopNode.INS;
            }
        }
        return new IfStatement(ifStatement.getPos(), expressionNode, getBlock(ts, Block.Type.IF), getBlock(es, Block.Type.ELSE));
    }

    private static Block getBlock(Statement statement, Block.Type type) {
        Block tb;
        if (statement instanceof Block) {
            Predictions.assertTrue(((Block) statement).getType() == type, "not if block???");
            tb = (Block) statement;
        } else if (statement == null) {
            tb = null;
        } else {
            tb = new Block(statement.getPos(), Collections.singletonList(statement), type);
        }
        return tb;
    }

    private VarDef defIterateVar(String name) {
        if ("_".equals(name)) {
            return null;
        }
        return addVarDef(name, null);
    }

    @Override
    public Node visit(ForeachStatement foreachStatement) {
        ExpressionNode node = (ExpressionNode) foreachStatement.getCollection().accept(this);

        Identifier keyVarName = foreachStatement.getKeyVarName();
        Identifier valVarName = foreachStatement.getValVarName();
        Block iterableBlock = foreachStatement.getIterableBlock();
        Statement statement;
        enterScope();
        defIterateVar(keyVarName.getName());
        defIterateVar(valVarName.getName());
        statement = visitBlockAndExistScope(iterableBlock);

        if (statement == NoopNode.INS) {
            if (node.isConstant()) {
                return NoopNode.INS;
            }
            return new ExpressionStatement(foreachStatement.getPos(), node);
        }

        return new ForeachStatement(foreachStatement.getPos(), foreachStatement.getKeyVarName(), foreachStatement.getValVarName(), node, (Block) statement);
    }

    @Override
    public VariableDeclareStatement visit(VariableDeclareStatement variableDeclareStatement) {

        if (variableDeclareStatement.getInitialExp() != null) {
            ExpressionNode init = (ExpressionNode) variableDeclareStatement.getInitialExp().accept(this);
            variableDeclareStatement.setInitialExp(init);
        }
        addVarDef(variableDeclareStatement.getVariableName().getName(), variableDeclareStatement.getInitialExp());
        return variableDeclareStatement;
    }

    @Override
    public Node visit(ExpressionStatement expressionStatement) {
        ExpressionNode expression = expressionStatement.getExpression();
        ExpressionNode node = (ExpressionNode) expression.accept(this);
        if (expression == node) {
            return expressionStatement;
        }
        if (node.isConstant()) {
            return NoopNode.INS;
        }
        return new ExpressionStatement(node);
    }

    @Override
    public ContinueStatement visit(ContinueStatement breakStatement) {
        return breakStatement;
    }

    @Override
    public BreakStatement visit(BreakStatement breakStatement) {
        return breakStatement;
    }

    @Override
    public ReturnStatement visit(ReturnStatement returnStatement) {
        if (returnStatement.getExpression() != null) {
            ExpressionNode expressionNode = (ExpressionNode) returnStatement.getExpression().accept(this);
            return new ReturnStatement(returnStatement.getPos(), expressionNode);
        }
        return returnStatement;
    }

    @Override
    public Node visit(NoopNode noopNode) {
        return noopNode;
    }

    @Override
    public Node visit(TryCatchStatement tryCatchStatement) {
        Statement tryBlock = tryCatchStatement.getTryBlock();
        Node node = tryBlock.accept(this);
        if (node == NoopNode.INS) {
            return node;
        }
        tryCatchStatement.setTryBlock((Statement) node);
        enterScope();
        addVarDef(tryCatchStatement.getExpVarName().getName(), null);
        node = tryCatchStatement.getCatchBlock().accept(this);
        tryCatchStatement.setCatchBlock((Statement) node);
        exitScope();
        return tryCatchStatement;
    }

    @Override
    public Node visit(ThrowStatement throwStatement) {
        ExpressionNode expressionNode = (ExpressionNode) throwStatement.getExpressionNode().accept(this);
        throwStatement.setExpressionNode(expressionNode);
        return throwStatement;
    }
}
