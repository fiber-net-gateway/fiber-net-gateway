package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.HashMap;

public final class CommonSubexpressionEliminationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        boolean changed = false;
        for (SsaBlock block : graph.blocks()) {
            HashMap<ValueKey, SsaValue> local = new HashMap<>();
            for (SsaValue value : new ArrayList<>(block.instructions())) {
                if (!isCandidate(value)) {
                    continue;
                }
                ValueKey key = ValueKey.forValue(value);
                SsaValue leader = local.get(key);
                if (leader == null) {
                    local.put(key, value);
                } else {
                    changed |= SsaEditor.replaceAllUses(graph, value, leader);
                }
            }
        }
        if (changed) {
            new DeadCodeEliminationPass().run(graph);
        }
        return changed;
    }

    static boolean isCandidate(SsaValue value) {
        Bytecode bytecode = value.bytecode();
        if (bytecode != null && bytecode.mayThrow()) {
            return false;
        }
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case BINARY:
            case UNARY:
                return true;
            default:
                return false;
        }
    }
}
