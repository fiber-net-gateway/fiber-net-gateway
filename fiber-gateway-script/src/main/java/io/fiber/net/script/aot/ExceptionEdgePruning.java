package io.fiber.net.script.aot;

import java.util.List;

public class ExceptionEdgePruning {

    private final Cfg cfg;

    public ExceptionEdgePruning(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            if (canThrow(block)) {
                continue;
            }
            for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                if (edge.type == Edge.Type.THROW) {
                    cfg.removeEdge(edge);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean canThrow(Block block) {
        List<Instruction> instructions = block.getInstructions();
        if (instructions.isEmpty()) {
            return false;
        }
        return instructions.get(instructions.size() - 1).canThrow() != Instruction.Throw.NOT;
    }
}
