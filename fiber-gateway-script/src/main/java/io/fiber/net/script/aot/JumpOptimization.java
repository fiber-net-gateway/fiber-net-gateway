package io.fiber.net.script.aot;

import java.util.List;

public class JumpOptimization {

    private final Cfg cfg;

    public JumpOptimization(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                if (edge.type != Edge.Type.JUMP) {
                    continue;
                }
                Block target = jumpOnlyTarget(edge.successor);
                if (target == null || target == edge.successor || target == block) {
                    continue;
                }
                cfg.removeEdge(edge);
                cfg.addEdge(edge.type, block, target);
                retargetTerminalJump(block, edge.successor, target);
                changed = true;
            }
        }
        return changed;
    }

    private static Block jumpOnlyTarget(Block block) {
        if (!block.getPhiValues().isEmpty()) {
            return null;
        }
        List<Instruction> instructions = block.getInstructions();
        if (instructions.size() != 1 || !(instructions.get(0) instanceof Jump)) {
            return null;
        }
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.JUMP) {
                return edge.successor.getPhiValues().isEmpty() ? edge.successor : null;
            }
        }
        return null;
    }

    private static void retargetTerminalJump(Block block, Block oldTarget, Block newTarget) {
        List<Instruction> instructions = block.getInstructions();
        if (instructions.isEmpty()) {
            return;
        }
        Instruction terminal = instructions.get(instructions.size() - 1);
        if (terminal instanceof Jump && ((Jump) terminal).getTarget() == oldTarget) {
            block.replaceInstruction(terminal, new Jump(block, terminal.getPc(), newTarget));
        } else if (terminal instanceof JumpIfTrue && ((JumpIfTrue) terminal).getTarget() == oldTarget) {
            JumpIfTrue jump = (JumpIfTrue) terminal;
            JumpIfTrue replacement = new JumpIfTrue(block, jump.getPc(), newTarget, jump.getCond());
            jump.dropOperands();
            block.replaceInstruction(jump, replacement);
        } else if (terminal instanceof JumpIfFalse && ((JumpIfFalse) terminal).getTarget() == oldTarget) {
            JumpIfFalse jump = (JumpIfFalse) terminal;
            JumpIfFalse replacement = new JumpIfFalse(block, jump.getPc(), newTarget, jump.getCond());
            jump.dropOperands();
            block.replaceInstruction(jump, replacement);
        }
    }
}
