package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

/**
 * Represents a variable reference, eg. #someVar. Note this is different to a *local* variable like $someVar
 *
 * @author Andy Clement
 * @since 3.0
 */
public class VariableReference extends ExpressionNode implements MaybeLValue {

    // Well known variables:
//    private final static String THIS = "this";  // currently active context object
    private final static String ROOT = "$";  // root context object

    private final String name;
    private boolean lValue;
    private boolean refConst;

    public VariableReference(String variableName, int pos) {
        super(pos);
        name = variableName;
    }

    public String getName() {
        return name;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(name);
    }

    public boolean isRoot() {
        return this.name.equals(ROOT);
    }

    @Override
    public boolean isConstant() {
        return !isRoot() && !lValue && refConst;
    }

    @Override
    public boolean isLValue() {
        return lValue;
    }

    @Override
    public void markLValue() {
        lValue = true;
    }

    public void markRefConst() {
        refConst = true;
    }
}
