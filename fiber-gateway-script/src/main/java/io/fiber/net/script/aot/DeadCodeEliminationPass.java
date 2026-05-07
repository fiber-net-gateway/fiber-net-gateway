package io.fiber.net.script.aot;

import java.util.ArrayList;

public final class DeadCodeEliminationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        boolean changed = false;
        boolean localChanged;
        do {
            localChanged = false;
            for (SsaBlock block : graph.blocks()) {
                for (SsaValue phi : new ArrayList<>(block.phis())) {
                    if (phi.uses().isEmpty()) {
                        localChanged |= SsaEditor.removeValue(phi);
                    }
                }
                for (SsaValue value : new ArrayList<>(block.instructions())) {
                    if (value.uses().isEmpty() && SsaEffects.isPure(value)) {
                        localChanged |= SsaEditor.removeValue(value);
                    }
                }
            }
            changed |= localChanged;
        } while (localChanged);
        return changed;
    }
}
