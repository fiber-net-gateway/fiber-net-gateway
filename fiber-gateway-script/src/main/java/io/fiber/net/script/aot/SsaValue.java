package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SsaValue {
    private final int id;
    private final SsaKind kind;
    private final SsaBlock block;
    private final Bytecode bytecode;
    private final int index;
    private final List<SsaValue> operands = new ArrayList<>();
    private final List<SsaUse> uses = new ArrayList<>();

    SsaValue(int id, SsaKind kind, SsaBlock block, Bytecode bytecode, int index, List<SsaValue> operands) {
        this.id = id;
        this.kind = kind;
        this.block = block;
        this.bytecode = bytecode;
        this.index = index;
        setOperands(operands);
    }

    public int id() {
        return id;
    }

    public SsaKind kind() {
        return kind;
    }

    public SsaBlock block() {
        return block;
    }

    public BasicBlock basicBlock() {
        return block == null ? null : block.basicBlock();
    }

    public Bytecode bytecode() {
        return bytecode;
    }

    public int index() {
        return index;
    }

    public List<SsaValue> operands() {
        return Collections.unmodifiableList(operands);
    }

    public List<SsaUse> uses() {
        return Collections.unmodifiableList(uses);
    }

    boolean addOperand(SsaValue value) {
        if (value == null) {
            throw new SsaException("ssa operand is null");
        }
        for (SsaValue operand : operands) {
            if (operand == value) {
                return false;
            }
        }
        appendOperand(value);
        return true;
    }

    void setOperands(List<SsaValue> values) {
        clearOperands();
        if (values == null) {
            return;
        }
        for (SsaValue value : values) {
            appendOperand(value);
        }
    }

    private void clearOperands() {
        for (int i = 0; i < operands.size(); i++) {
            operands.get(i).removeUse(this, i);
        }
        operands.clear();
    }

    private void appendOperand(SsaValue value) {
        if (value == null) {
            throw new SsaException("ssa operand is null");
        }
        int operandIndex = operands.size();
        operands.add(value);
        value.addUse(new SsaUse(value, this, operandIndex));
    }

    void replaceOperand(int operandIndex, SsaValue value) {
        if (value == null) {
            throw new SsaException("ssa replacement operand is null");
        }
        SsaValue old = operands.get(operandIndex);
        if (old == value) {
            return;
        }
        old.removeUse(this, operandIndex);
        operands.set(operandIndex, value);
        value.addUse(new SsaUse(value, this, operandIndex));
    }

    void clearUses() {
        uses.clear();
    }

    private void addUse(SsaUse use) {
        uses.add(use);
    }

    private void removeUse(SsaValue user, int operandIndex) {
        for (int i = uses.size() - 1; i >= 0; i--) {
            SsaUse use = uses.get(i);
            if (use.user() == user && use.operandIndex() == operandIndex) {
                uses.remove(i);
                return;
            }
        }
    }

    boolean isPhi() {
        return kind == SsaKind.PHI;
    }

    @Override
    public String toString() {
        return '%' + Integer.toString(id) + ':' + kind + (index >= 0 ? "[" + index + "]" : "");
    }
}
