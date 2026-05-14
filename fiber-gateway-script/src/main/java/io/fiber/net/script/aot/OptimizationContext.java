package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class OptimizationContext {
    private final Cfg cfg;
    private Dominators dominators;
    private Set<Block> reachableBlocks;

    OptimizationContext(Cfg cfg) {
        this.cfg = cfg;
    }

    Dominators dominators() {
        if (dominators == null) {
            dominators = Dominators.compute(cfg);
        }
        return dominators;
    }

    Set<Block> reachableBlocks() {
        if (reachableBlocks == null) {
            reachableBlocks = computeReachableBlocks();
        }
        return reachableBlocks;
    }

    void invalidateControlFlow() {
        dominators = null;
        reachableBlocks = null;
    }

    boolean removeUnreachableBlocks() {
        Set<Block> reachable = reachableBlocks();
        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            if (reachable.contains(block)) {
                continue;
            }
            cfg.removeBlock(block);
            changed = true;
        }
        if (changed) {
            invalidateControlFlow();
        }
        return changed;
    }

    private Set<Block> computeReachableBlocks() {
        Set<Block> reachable = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(cfg.getEntryBlock());
        while (!queue.isEmpty()) {
            Block block = queue.poll();
            if (!reachable.add(block)) {
                continue;
            }
            for (Edge edge : block.getSuccessors()) {
                queue.add(edge.successor);
            }
        }
        return reachable;
    }
}
