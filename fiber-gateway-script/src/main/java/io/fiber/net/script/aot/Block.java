package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public class Block {

    private final List<Instruction> instructions = new ArrayList<>();

    final List<Link> predecessors = new ArrayList<>();
    final List<Link> successors = new ArrayList<>();
    final int startPc;
    int endPc; // exclude. immutable
    private List<SsaValue> phiValues;

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

    public List<Link> getPredecessors() {
        return predecessors;
    }

    public List<Link> getSuccessors() {
        return successors;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }
}
