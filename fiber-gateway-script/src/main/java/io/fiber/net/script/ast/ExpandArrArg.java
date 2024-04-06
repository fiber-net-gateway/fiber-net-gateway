package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ExpandArrArg extends ExpressionNode {

    public enum Where {
        INIT_OBJ,
        INIT_ARR,
        FUNC_CALL,
    }

    private final ExpressionNode operand;
    private final Where where;

    public ExpandArrArg(int pos, ExpressionNode operand, Where where) {
        super(pos);
        this.operand = operand;
        this.where = where;
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

    public Where getWhere() {
        return where;
    }
}
