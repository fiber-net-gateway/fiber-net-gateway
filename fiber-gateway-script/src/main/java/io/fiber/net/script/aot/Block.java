package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public class Block {

    private final List<Instruction> instructions = new ArrayList<>();

    final List<Edge> predecessors = new ArrayList<>();
    final List<Edge> successors = new ArrayList<>();
    final int startPc;
    int endPc; // exclude. immutable
    private List<SsaValue> phiValues;

    int inputStackSize; // stackSize when enter block
    int legacyStackSize; // stackSize legacy (do not contain stack size produced by this) when lease block
    int outputStackSize; // produced stackSize by this block

    public Block(int startPc) {
        this.startPc = startPc;
    }

    public void addPhi(Phi phi) {
        if (phiValues == null) {
            phiValues = new ArrayList<>();
        }
        phiValues.add(new SsaValue(phi));
    }

    public Phi newPhi() {
        return new Phi(this, startPc);
    }

    public List<SsaValue> getPhiValues() {
        return phiValues;
    }

    public List<Edge> getPredecessors() {
        return predecessors;
    }

    public List<Edge> getSuccessors() {
        return successors;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }
}
