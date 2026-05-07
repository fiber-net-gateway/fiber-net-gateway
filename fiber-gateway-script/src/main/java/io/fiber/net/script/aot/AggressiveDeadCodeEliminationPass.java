package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

public final class AggressiveDeadCodeEliminationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        PostDominatorTree postDominatorTree = new PostDominatorTree(graph.cfg());
        ControlDependenceGraph controlDependenceGraph = new ControlDependenceGraph(graph.cfg(), postDominatorTree);
        HashSet<SsaValue> live = markLive(graph, controlDependenceGraph);
        return sweep(graph, live);
    }

    private static HashSet<SsaValue> markLive(SsaGraph graph, ControlDependenceGraph controlDependenceGraph) {
        HashSet<SsaValue> live = new HashSet<>();
        ArrayDeque<SsaValue> workList = new ArrayDeque<>();
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.phis()) {
                if (SsaEffects.isRequiredRoot(value)) {
                    mark(value, live, workList);
                }
            }
            for (SsaValue value : block.instructions()) {
                if (SsaEffects.isRequiredRoot(value)) {
                    mark(value, live, workList);
                }
            }
        }

        while (!workList.isEmpty()) {
            SsaValue value = workList.poll();
            for (SsaValue operand : value.operands()) {
                mark(operand, live, workList);
            }
            SsaBlock block = value.block();
            if (block != null) {
                for (BasicBlock controller : controlDependenceGraph.controllers(block.basicBlock())) {
                    SsaValue terminator = terminator(graph.blockFor(controller));
                    if (terminator != null) {
                        mark(terminator, live, workList);
                    }
                }
            }
        }
        return live;
    }

    private static boolean sweep(SsaGraph graph, HashSet<SsaValue> live) {
        boolean changed = false;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue phi : new ArrayList<>(block.phis())) {
                if (!live.contains(phi) && SsaEffects.isAdceRemovable(phi)) {
                    changed |= SsaEditor.forceRemoveValue(phi);
                }
            }
            for (SsaValue value : new ArrayList<>(block.instructions())) {
                if (!live.contains(value) && SsaEffects.isAdceRemovable(value)) {
                    changed |= SsaEditor.forceRemoveValue(value);
                }
            }
        }
        return changed;
    }

    private static void mark(SsaValue value, HashSet<SsaValue> live, ArrayDeque<SsaValue> workList) {
        if (value != null && live.add(value)) {
            workList.add(value);
        }
    }

    private static SsaValue terminator(SsaBlock block) {
        if (block == null) {
            return null;
        }
        for (int i = block.instructions().size() - 1; i >= 0; i--) {
            SsaValue value = block.instructions().get(i);
            if (SsaEffects.isControl(value)) {
                return value;
            }
        }
        return null;
    }
}
