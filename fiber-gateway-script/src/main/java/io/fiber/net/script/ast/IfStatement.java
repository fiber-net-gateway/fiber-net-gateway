package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class IfStatement extends Statement {

    private final ExpressionNode predict;
    private final Statement trueBlock;
    private final Statement elseStatement;

    public IfStatement(int pos, ExpressionNode predict, Statement trueBlock, Statement elseStatement) {
        super(pos);
        this.predict = predict;
        this.trueBlock = trueBlock;
        this.elseStatement = elseStatement;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("if (");
        predict.toStringAST(sb);
        sb.append(")");
        trueBlock.toStringAST(sb);
        if (elseStatement != null) {
            sb.append(" else ");
            elseStatement.toStringAST(sb);
        }
    }

    public ExpressionNode getPredict() {
        return predict;
    }

    public Statement getTrueBlock() {
        return trueBlock;
    }

    public Statement getElseStatement() {
        return elseStatement;
    }
}
