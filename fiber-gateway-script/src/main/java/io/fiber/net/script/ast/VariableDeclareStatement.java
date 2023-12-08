package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class VariableDeclareStatement extends Statement {

    private final Identifier variableName;
    private ExpressionNode initialExp;

    public VariableDeclareStatement(int pos, Identifier variableName, ExpressionNode initialExp) {
        super(pos);
        this.variableName = variableName;
        this.initialExp = initialExp;
    }

    public void setInitialExp(ExpressionNode initialExp) {
        this.initialExp = initialExp;
    }

    public Identifier getVariableName() {
        return variableName;
    }

    public ExpressionNode getInitialExp() {
        return initialExp;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("let ").append(variableName.getName());
        if (initialExp != null) {
            sb.append(" = ");
            initialExp.toStringAST(sb);
        }
        sb.append(';');
    }
}
