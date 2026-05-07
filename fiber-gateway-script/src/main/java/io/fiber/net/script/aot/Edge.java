package io.fiber.net.script.aot;

public final class Edge {
    private final BasicBlock from;
    private final BasicBlock to;
    private final EdgeKind kind;

    Edge(BasicBlock from, BasicBlock to, EdgeKind kind) {
        this.from = from;
        this.to = to;
        this.kind = kind;
    }

    public BasicBlock from() {
        return from;
    }

    public BasicBlock to() {
        return to;
    }

    public EdgeKind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return "B" + from.id() + " -" + kind + "-> B" + to.id();
    }
}
