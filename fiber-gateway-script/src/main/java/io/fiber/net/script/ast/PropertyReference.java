package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class PropertyReference extends ExpressionNode implements MaybeLValue {

    private final String name;
    private final ExpressionNode target;
    private boolean lValue;


    public PropertyReference(String propertyOrFieldName, int pos, ExpressionNode target) {
        super(pos);
        this.name = propertyOrFieldName;
        this.target = target;
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public String getName() {
        return name;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append('.').append(name);
    }


    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
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
        return !isLValue() && target.isConstant();
    }
}
