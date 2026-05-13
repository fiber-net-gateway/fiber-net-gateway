package io.fiber.net.script.aot;

import java.util.List;

public class AlwaysThrowPruning {

    private final Cfg cfg;
    private final OptimizationContext context;

    public AlwaysThrowPruning(Cfg cfg) {
        this(cfg, new OptimizationContext(cfg));
    }

    AlwaysThrowPruning(Cfg cfg, OptimizationContext context) {
        this.cfg = cfg;
        this.context = context;
    }

    public boolean optimize() {
        boolean changed = pruneFallthroughAfterAlwaysThrow();
        if (changed) {
            context.invalidateControlFlow();
        }
        return context.removeUnreachableBlocks() || changed;
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

}
