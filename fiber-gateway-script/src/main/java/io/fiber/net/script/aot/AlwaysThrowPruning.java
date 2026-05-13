package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class AlwaysThrowPruning {

    private final Cfg cfg;

    public AlwaysThrowPruning(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = pruneFallthroughAfterAlwaysThrow();
        return removeUnreachableBlocks() || changed;
    }

    private boolean pruneFallthroughAfterAlwaysThrow() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            Instruction terminal = terminal(block);
            if (terminal == null || terminal instanceof Throw
                    || terminal.canThrow() != Instruction.Throw.ALWAYS) {
                continue;
            }
            for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                if (edge.type != Edge.Type.THROW) {
                    cfg.removeEdge(edge);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Instruction terminal(Block block) {
        List<Instruction> instructions = block.getInstructions();
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    private boolean removeUnreachableBlocks() {
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

        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            if (reachable.contains(block)) {
                continue;
            }
            cfg.removeBlock(block);
            changed = true;
        }
        return changed;
    }
}
