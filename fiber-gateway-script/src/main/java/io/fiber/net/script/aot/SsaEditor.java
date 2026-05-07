package io.fiber.net.script.aot;

import java.util.ArrayList;

final class SsaEditor {
    private SsaEditor() {
    }

    static boolean replaceAllUses(SsaGraph graph, SsaValue oldValue, SsaValue newValue) {
        if (oldValue == newValue) {
            return false;
        }
        ArrayList<SsaUse> uses = new ArrayList<>(oldValue.uses());
        for (SsaUse use : uses) {
            use.user().replaceOperand(use.operandIndex(), newValue);
        }
        for (SsaBlock block : graph.blocks()) {
            block.replaceStateValue(oldValue, newValue);
        }
        return !uses.isEmpty();
    }

    static boolean removeValue(SsaValue value) {
        if (!value.uses().isEmpty()) {
            return false;
        }
        value.setOperands(null);
        SsaBlock block = value.block();
        if (block == null) {
            return false;
        }
        return value.isPhi() ? block.removePhi(value) : block.removeInstruction(value);
    }

    static boolean forceRemoveValue(SsaValue value) {
        value.setOperands(null);
        value.clearUses();
        SsaBlock block = value.block();
        if (block == null) {
            return false;
        }
        return value.isPhi() ? block.removePhi(value) : block.removeInstruction(value);
    }
}
