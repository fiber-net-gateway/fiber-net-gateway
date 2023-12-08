package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class UnaryOperator extends ExpressionNode {
    private final Operator operator;
    private final ExpressionNode node;

    public UnaryOperator(int pos, Operator operator, ExpressionNode node) {
        super(pos);
        this.operator = operator;
        this.node = node;
    }

    public Operator getOperator() {
        return operator;
    }

    public ExpressionNode getTarget() {
        return node;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(operator.getPayload());
        node.toStringAST(sb);
    }

    @Override
    public boolean isConstant() {
        return node.isConstant();
    }
}
