package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ExpressionStatement extends Statement {
    private final ExpressionNode expression;

    public ExpressionStatement(ExpressionNode expression) {
        super(expression.pos);
        this.expression = expression;
    }

    public ExpressionStatement(int pos, ExpressionNode expression) {
        super(pos);
        this.expression = expression;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        expression.toStringAST(sb);
        sb.append(';');
    }

    public ExpressionNode getExpression() {
        return expression;
    }
}
