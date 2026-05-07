package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SsaBlock {
    private final BasicBlock basicBlock;
    private final List<SsaValue> phis = new ArrayList<>();
    private final List<SsaValue> instructions = new ArrayList<>();
    private final SsaValue[] stackPhis;
    private final SsaValue[] varPhis;
    private final SsaValue[] instructionValues;
    private final SsaValue[] exceptionValues;
    private SsaValue exceptionPhi;

    StackState inStack;
    StackState outStack;
    VarState inVars;
    VarState outVars;
    SsaValue exceptionValue;

    SsaBlock(BasicBlock basicBlock, int maxStack, int varCount) {
        this.basicBlock = basicBlock;
        this.stackPhis = new SsaValue[Math.max(0, maxStack)];
        this.varPhis = new SsaValue[Math.max(0, varCount)];
        this.instructionValues = new SsaValue[basicBlock.codeLength()];
        this.exceptionValues = new SsaValue[basicBlock.codeLength()];
    }

    public BasicBlock basicBlock() {
        return basicBlock;
    }

    public int id() {
        return basicBlock.id();
    }

    public List<SsaValue> phis() {
        return Collections.unmodifiableList(phis);
    }

    public List<SsaValue> instructions() {
        return Collections.unmodifiableList(instructions);
    }

    public StackState inStack() {
        return inStack == null ? null : inStack.copy();
    }

    public StackState outStack() {
        return outStack == null ? null : outStack.copy();
    }

    public VarState inVars() {
        return inVars == null ? null : inVars.copy();
    }

    public VarState outVars() {
        return outVars == null ? null : outVars.copy();
    }

    public SsaValue exceptionValue() {
        return exceptionValue;
    }

    void addInstruction(SsaValue value) {
        instructions.add(value);
    }

    void addInstructionBefore(SsaValue before, SsaValue value) {
        int index = instructions.indexOf(before);
        if (index < 0) {
            instructions.add(value);
        } else {
            instructions.add(index, value);
        }
    }

    boolean removePhi(SsaValue value) {
        return phis.remove(value);
    }

    boolean removeInstruction(SsaValue value) {
        return instructions.remove(value);
    }

    boolean replaceStateValue(SsaValue oldValue, SsaValue newValue) {
        boolean changed = false;
        if (inStack != null) {
            changed |= inStack.replace(oldValue, newValue);
        }
        if (outStack != null) {
            changed |= outStack.replace(oldValue, newValue);
        }
        if (inVars != null) {
            changed |= inVars.replace(oldValue, newValue);
        }
        if (outVars != null) {
            changed |= outVars.replace(oldValue, newValue);
        }
        if (exceptionValue == oldValue) {
            exceptionValue = newValue;
            changed = true;
        }
        return changed;
    }

    SsaValue instruction(int codeIndex,
                         SsaKind kind,
                         Bytecode bytecode,
                         int index,
                         List<SsaValue> operands,
                         SsaValueFactory factory) {
        SsaValue value = instructionValues[codeIndex];
        if (value == null) {
            value = factory.create(kind, this, bytecode, index, operands);
            instructionValues[codeIndex] = value;
            instructions.add(value);
            return value;
        }
        if (value.kind() != kind) {
            throw new SsaException("ssa instruction kind changed at pc " + bytecode.pc()
                    + ": " + value.kind() + " -> " + kind);
        }
        value.setOperands(operands);
        return value;
    }

    SsaValue stackPhi(int index, SsaValueFactory factory) {
        SsaValue phi = stackPhis[index];
        if (phi == null) {
            phi = factory.create(SsaKind.PHI, this, null, index, null);
            stackPhis[index] = phi;
            phis.add(phi);
        }
        return phi;
    }

    SsaValue varPhi(int index, SsaValueFactory factory) {
        SsaValue phi = varPhis[index];
        if (phi == null) {
            phi = factory.create(SsaKind.PHI, this, null, index, null);
            varPhis[index] = phi;
            phis.add(phi);
        }
        return phi;
    }

    SsaValue exceptionPhi(SsaValueFactory factory) {
        if (exceptionPhi == null) {
            exceptionPhi = factory.create(SsaKind.PHI, this, null, -1, null);
            phis.add(exceptionPhi);
        }
        return exceptionPhi;
    }

    SsaValue exceptionAt(int codeIndex, Bytecode bytecode, SsaValueFactory factory) {
        SsaValue value = exceptionValues[codeIndex];
        if (value == null) {
            value = factory.create(SsaKind.EXCEPTION, this, bytecode, -1, null);
            exceptionValues[codeIndex] = value;
        }
        return value;
    }

    @Override
    public String toString() {
        return "SsaBlock{" + basicBlock + '}';
    }
}
