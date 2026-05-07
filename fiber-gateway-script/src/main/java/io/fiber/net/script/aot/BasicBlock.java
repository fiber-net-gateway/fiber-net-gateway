package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BasicBlock {
    private final int id;
    private final int startPc;
    private final int endPc;
    private final Bytecode[] codes;
    private final List<Edge> successors = new ArrayList<>();
    private final List<Edge> predecessors = new ArrayList<>();

    BasicBlock(int id, int startPc, int endPc, Bytecode[] codes) {
        this.id = id;
        this.startPc = startPc;
        this.endPc = endPc;
        this.codes = codes;
    }

    SsaBlock newSsaBlock(int maxStack, int varCount) {
        return new SsaBlock(this, maxStack, varCount);
    }

    public int id() {
        return id;
    }

    public int startPc() {
        return startPc;
    }

    public int endPc() {
        return endPc;
    }

    public int codeLength() {
        return codes.length;
    }

    public Bytecode[] codes() {
        return codes.clone();
    }

    public Bytecode codeAt(int index) {
        return codes[index];
    }

    public Bytecode firstCode() {
        return codes[0];
    }

    public Bytecode lastCode() {
        return codes[codes.length - 1];
    }

    public List<Edge> successors() {
        return Collections.unmodifiableList(successors);
    }

    public List<Edge> predecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    void addEdgeTo(BasicBlock to, EdgeKind kind) {
        for (Edge edge : successors) {
            if (edge.to() == to && edge.kind() == kind) {
                return;
            }
        }
        Edge edge = new Edge(this, to, kind);
        successors.add(edge);
        to.predecessors.add(edge);
    }

    @Override
    public String toString() {
        return "B" + id + "[" + startPc + ',' + endPc + ')';
    }
}
