package io.fiber.net.script.ast;

import io.fiber.net.script.parse.Node;
import io.fiber.net.script.parse.NodeVisitor;

public abstract class Statement implements Node {

    protected int pos; // start = top 16bits, end = bottom 16bits

    public Statement(int pos) {
        this.pos = pos;
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

    @Override
    public abstract <T> T accept(NodeVisitor<T> nodeVisitor);
}
