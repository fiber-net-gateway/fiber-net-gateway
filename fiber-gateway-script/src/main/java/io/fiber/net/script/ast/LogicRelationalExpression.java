package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class LogicRelationalExpression extends ExpressionNode {
    private final ExpressionNode left;
    private final Operator operator;
    private final ExpressionNode right;

    public LogicRelationalExpression(int pos, ExpressionNode left, Operator operator, ExpressionNode right) {
        super(pos);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        left.toStringAST(sb);
        sb.append(operator.getAstPayload());
        right.toStringAST(sb);
    }

    @Override
    public boolean isConstant() {
        return left.isConstant() && right.isConstant();
    }
}
