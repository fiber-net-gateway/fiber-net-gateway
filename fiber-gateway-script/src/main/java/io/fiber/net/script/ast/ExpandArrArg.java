package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ExpandArrArg extends ExpressionNode {

    private final ExpressionNode operand;

    public ExpandArrArg(int pos, ExpressionNode operand) {
        super(pos);
        this.operand = operand;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("...");
        operand.toStringAST(sb);
    }

    public ExpressionNode getOperand() {
        return operand;
    }

    @Override
    public boolean isConstant() {
        return operand.isConstant();
    }

}
