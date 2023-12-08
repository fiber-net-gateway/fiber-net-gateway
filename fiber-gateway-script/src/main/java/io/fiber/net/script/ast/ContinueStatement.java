package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class ContinueStatement extends Statement{
    public ContinueStatement(int pos) {
        super(pos);
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append("continue;");
    }
}
