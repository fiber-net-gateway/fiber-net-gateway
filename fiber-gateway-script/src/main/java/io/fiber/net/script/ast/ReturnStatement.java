package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ReturnStatement extends Statement {
    private final ExpressionNode expression;

    public ReturnStatement(int pos, ExpressionNode expression) {
        super(pos);
        this.expression = expression;
    }

    public ExpressionNode getExpression() {
        return expression;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("return ");
        if (expression != null) {
            expression.toStringAST(sb);
        }
        sb.append(';');
    }
}
