package io.fiber.net.script.aot;

import java.util.ArrayList;

public final class CopyPropagationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        boolean changed = false;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : new ArrayList<>(block.instructions())) {
                if (!SsaEffects.isCopy(value)) {
                    continue;
                }
                SsaValue replacement = value.operands().get(0);
                changed |= SsaEditor.replaceAllUses(graph, value, replacement);
            }
        }
        return changed;
    }
}
