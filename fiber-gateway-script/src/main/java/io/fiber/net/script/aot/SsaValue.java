package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNodeType;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.Predictions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SsaValue {
    public enum Type {
        ARRAY,
        BINARY,
        BOOLEAN,
        MISSING,
        NULL,
        NUMBER,
        OBJECT,
        STRING,
        // for script
        EXCEPTION,
        ITERATOR,
        Unknown,
    }

    private static final Type[] VALUES = Type.values();

    public static Type ofType(JsonNodeType v) {
        if (v == null) {
            return Type.Unknown;
        }
        return VALUES[v.ordinal()];
    }

    private Expr assign;
    private List<Instruction> used;

    public SsaValue(Expr assign) {
        this.assign = assign;
    }

    public void replaceAssign(Expr assign) {
        this.assign = assign;
        assign.setResult(this);
    }

    public int getUsedCount() {
        if (CollectionUtils.isEmpty(used)) {
            return 0;
        }
        return used.size();
    }

    public Expr getAssign() {
        return assign;
    }

    public void addUsed(Instruction usedIns) {
        if (used == null) {
            used = new ArrayList<>();
        }
        used.add(usedIns);
    }

    public void replaceAllUsesWith(SsaValue newVal) {
        if (newVal == this) {
            return;
        }
        List<Instruction> used = this.used;
        while (!CollectionUtils.isEmpty(used)) {
            Instruction instruction = used.get(used.size() - 1);
            int replaced = instruction.replaceOperand(this, newVal);
            Predictions.assertTrue(replaced>=0, "[bug] used value cannot replaced operand");
            removeUsed(instruction, replaced);
            for (int i = 0; i < replaced; i++) {
                newVal.addUsed(instruction);
            }
        }
    }

    private void removeUsed(Instruction instruction, int count) {
        if (used == null) {
            return;
        }
        for (int i = used.size() - 1; i >= 0 && count > 0; i--) {
            if (used.get(i) == instruction) {
                used.remove(i);
                count--;
            }
        }
        if (used.isEmpty()) {
            used = null;
        }
    }

    void removeUsed(Instruction instruction) {
        removeUsed(instruction, 1);
    }

    public List<Instruction> getUsed() {
        if (used == null) {
            return Collections.emptyList();
        }
        return used;
    }

    public Type getType() {
        return assign.getResultType();
    }
}
