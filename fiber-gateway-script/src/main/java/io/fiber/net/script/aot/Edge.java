package io.fiber.net.script.aot;

public class Edge {
    public enum Type {
        FALLTHROUGH,
        JUMP,
        THROW,
    }

    final Type type;
    final Block predecessor;
    final Block successor;

    public Edge(Type type, Block predecessor, Block successor) {
        this.type = type;
        this.predecessor = predecessor;
        this.successor = successor;
        predecessor.successors.add(this);
        successor.predecessors.add(this);
    }

    public Block getPredecessor() {
        return predecessor;
    }

    public Block getSuccessor() {
        return successor;
    }

    public Type getType() {
        return type;
    }
}
