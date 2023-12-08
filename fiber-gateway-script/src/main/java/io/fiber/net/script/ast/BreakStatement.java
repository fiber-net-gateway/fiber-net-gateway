package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class BreakStatement extends Statement{
    public BreakStatement(int pos) {
        super(pos);
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("break;");
    }
}
