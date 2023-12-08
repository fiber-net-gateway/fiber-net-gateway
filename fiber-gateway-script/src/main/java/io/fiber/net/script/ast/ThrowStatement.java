package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ThrowStatement extends Statement {
    private ExpressionNode expressionNode;

    public ThrowStatement(int pos, ExpressionNode expressionNode) {
        super(pos);
        this.expressionNode = expressionNode;
    }

    public void setExpressionNode(ExpressionNode expressionNode) {
        this.expressionNode = expressionNode;
    }

    public ExpressionNode getExpressionNode() {
        return expressionNode;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("throw ");
        expressionNode.toStringAST(sb);
        sb.append(';');
    }
}
