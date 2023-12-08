package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ForeachStatement extends Statement {
    private final Identifier keyVarName;
    private final Identifier valVarName;
    private final ExpressionNode collection;
    private final Block iterableBlock;

    public ForeachStatement(int pos, Identifier keyVarName, Identifier valVarName, ExpressionNode collection, Block iterableBlock) {
        super(pos);
        this.keyVarName = keyVarName;
        this.valVarName = valVarName;
        this.collection = collection;
        this.iterableBlock = iterableBlock;
    }

    public Identifier getKeyVarName() {
        return keyVarName;
    }

    public Identifier getValVarName() {
        return valVarName;
    }

    public ExpressionNode getCollection() {
        return collection;
    }

    public Block getIterableBlock() {
        return iterableBlock;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("for(").append("let ")
                .append(keyVarName.getName()).append(", ")
                .append(valVarName.getName())
                .append(" of ");
        collection.toStringAST(sb);
        sb.append(')');
        iterableBlock.toStringAST(sb);
    }
}
