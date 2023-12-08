package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class Indexer extends ExpressionNode implements MaybeLValue {
    private final ExpressionNode parent;
    private final ExpressionNode key;
    private boolean lValue;

    public Indexer(int pos, ExpressionNode parent, ExpressionNode key) {
        super(pos);
        this.parent = parent;
        this.key = key;
    }

    public ExpressionNode getParent() {
        return parent;
    }

    public ExpressionNode getKey() {
        return key;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        parent.toStringAST(sb);
        sb.append("[");
        key.toStringAST(sb);
        sb.append("]");
    }


    @Override
    public boolean isLValue() {
        return lValue;
    }

    @Override
    public void markLValue() {
        lValue = true;
    }

    @Override
    public boolean isConstant() {
        return !isLValue() && parent.isConstant() && key.isConstant();
    }
}
