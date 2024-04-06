package io.fiber.net.script.ast;

import io.fiber.net.common.utils.Predictions;
import io.fiber.net.script.parse.NodeVisitor;

import java.util.Collections;

public class IfStatement extends Statement {

    private final ExpressionNode predict;
    private final Block trueBlock;
    private final Block elseBlock;

    public IfStatement(int pos, ExpressionNode predict, Block trueBlock, IfStatement elseIf) {
        super(pos);
        this.predict = predict;
        this.trueBlock = trueBlock;
        this.elseBlock = new Block(elseIf.getPos(), Collections.singletonList(elseIf), Block.Type.ELSE);
    }

    public IfStatement(int pos, ExpressionNode predict, Block trueBlock, Block elseBlock) {
        super(pos);
        Predictions.assertTrue(predict != null, "predict not null");
        Predictions.assertTrue(trueBlock != null, "predict not null");
        this.predict = predict;
        this.trueBlock = trueBlock;
        this.elseBlock = elseBlock;
    }

    public IfStatement(int pos, ExpressionNode predict, Block trueBlock) {
        this(pos, predict, trueBlock, (Block) null);
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
        if (elseBlock != null) {
            sb.append(" else ");
            elseBlock.toStringAST(sb);
        }
    }

    public ExpressionNode getPredict() {
        return predict;
    }

    public Block getTrueBlock() {
        return trueBlock;
    }


    public Block getElseBlock() {
        return elseBlock;
    }
}
