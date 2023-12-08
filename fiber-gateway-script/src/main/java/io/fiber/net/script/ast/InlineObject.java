package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class InlineObject extends ExpressionNode {
    @Override
    public boolean isConstant() {
        for (ExpressionNode valueChild : valueChildren) {
            if (!valueChild.isConstant()) {
                return false;
            }
        }
        return true;
    }

    public static class ExpandKey {
        private ExpandKey() {
        }
    }

    private static final ExpandKey EXPAND_KEY = new ExpandKey();

    public static Object expandKey() {
        return EXPAND_KEY;
    }

    public static boolean isExpandKey(Object key) {
        return EXPAND_KEY == key;
    }

    private final Object[] keys;
    private final ExpressionNode[] valueChildren;

    public InlineObject(int pos, Object[] keys, ExpressionNode[] vals) {
        super(pos);
        this.keys = keys;
        valueChildren = vals;
    }

    public Object[] getKeys() {
        return keys;
    }

    public ExpressionNode[] getValueChildren() {
        return valueChildren;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append('{');
        int length = keys.length;
        for (int i = 0; i < length; i++) {
            Object key = keys[i];
            if (isExpandKey(key)) {
                sb.append("...");
            } else {
                sb.append('"');
                sb.append(key);
                sb.append('"');
                sb.append(':');
            }
            valueChildren[i].toStringAST(sb);
            if (i != length - 1) {
                sb.append(',');
            }
        }
        sb.append('}');
    }
}
