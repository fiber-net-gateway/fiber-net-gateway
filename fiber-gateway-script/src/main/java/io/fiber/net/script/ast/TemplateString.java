package io.fiber.net.script.ast;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.parse.NodeVisitor;

public final class TemplateString extends ExpressionNode {

    private final String[] strings;
    private final ExpressionNode[] expressions;

    public TemplateString(int pos, String[] strings, ExpressionNode[] expressions) {
        super(pos);
        Assert.isTrue(strings.length == expressions.length + 1);
        this.strings = strings;
        this.expressions = expressions;
    }

    public String[] getStrings() {
        return strings;
    }

    public ExpressionNode[] getExpressions() {
        return expressions;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append('`');
        for (int i = 0; i < expressions.length; i++) {
            sb.append(strings[i]);
            sb.append("${");
            expressions[i].toStringAST(sb);
            sb.append('}');
        }
        sb.append(strings[strings.length - 1]);
        sb.append('`');
    }

    @Override
    public boolean isConstant() {
        for (ExpressionNode expression : expressions) {
            if (!expression.isConstant()) {
                return false;
            }
        }
        return true;
    }
}
