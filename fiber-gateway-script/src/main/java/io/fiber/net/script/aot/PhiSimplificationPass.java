package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public final class PhiSimplificationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        boolean changed = false;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue phi : new ArrayList<>(block.phis())) {
                SimplifiedPhi simplified = simplify(phi);
                if (simplified.operands != null) {
                    phi.setOperands(simplified.operands);
                    changed = true;
                }
                SsaValue replacement = simplified.replacement;
                if (replacement != null) {
                    SsaEditor.replaceAllUses(graph, phi, replacement);
                    changed |= SsaEditor.removeValue(phi);
                } else if (phi.uses().isEmpty()) {
                    changed |= SsaEditor.removeValue(phi);
                }
            }
        }
        return changed;
    }

    private static SimplifiedPhi simplify(SsaValue phi) {
        ArrayList<SsaValue> unique = new ArrayList<>(phi.operands().size());
        for (SsaValue operand : phi.operands()) {
            if (operand == phi) {
                continue;
            }
            if (!contains(unique, operand)) {
                unique.add(operand);
            }
        }
        if (unique.size() == 1) {
            return new SimplifiedPhi(unique.get(0), null);
        }
        if (unique.size() != phi.operands().size()) {
            return new SimplifiedPhi(null, unique);
        }
        return new SimplifiedPhi(null, null);
    }

    private static boolean contains(List<SsaValue> values, SsaValue value) {
        for (SsaValue existing : values) {
            if (existing == value) {
                return true;
            }
        }
        return false;
    }

    private static final class SimplifiedPhi {
        private final SsaValue replacement;
        private final List<SsaValue> operands;

        private SimplifiedPhi(SsaValue replacement, List<SsaValue> operands) {
            this.replacement = replacement;
            this.operands = operands;
        }
    }
}
