package io.fiber.net.script.ast;

import io.fiber.net.script.parse.Node;
import io.fiber.net.script.parse.NodeVisitor;

public class Identifier implements Node {
    private final int pos;
    private final String name;

    public Identifier(int pos, String name) {
        this.pos = pos;
        this.name = name;
    }

    @Override
    public <T> T accept(NodeVisitor<T> nodeVisitor) {
        return null;
    }

    @Override
    public void toStringAST(StringBuilder sb) {
        sb.append(name);
    }

    @Override
    public int getStartPosition() {
        return (pos >> 16);
    }

    @Override
    public int getEndPosition() {
        return (pos & 0xffff);
    }

    public int getPos() {
        return pos;
    }

    public String getName() {
        return name;
    }
}
