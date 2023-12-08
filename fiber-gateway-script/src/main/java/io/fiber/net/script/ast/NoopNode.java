package io.fiber.net.script.ast;

import io.fiber.net.script.parse.NodeVisitor;

public class NoopNode extends Statement {
    public static final NoopNode INS = new NoopNode();

    private NoopNode() {
        super(0);
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return nodeVisitor.visit(this);
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(';');
    }
}