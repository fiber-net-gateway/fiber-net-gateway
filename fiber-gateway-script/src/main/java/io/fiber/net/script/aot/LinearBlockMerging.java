package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public class LinearBlockMerging {

    private final Cfg cfg;

    public LinearBlockMerging(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            if (!cfg.getBlocks().contains(block)) {
                continue;
            }
            changed |= tryMerge(block);
        }
        return changed;
    }

    private boolean tryMerge(Block block) {
        if (block.getSuccessors().size() != 1) {
            return false;
        }

        Edge outgoing = block.getSuccessors().get(0);
        if (outgoing.type == Edge.Type.THROW) {
            return false;
        }

        Block target = outgoing.successor;
        if (target == block
                || target == cfg.getEntryBlock()
                || !target.getPhiValues().isEmpty()
                || target.getPredecessors().size() != 1
                || target.getPredecessors().get(0).predecessor != block) {
            return false;
        }

        if (!canRemoveTerminalJump(block, target)) {
            return false;
        }

        List<Edge> targetSuccessors = new ArrayList<>(target.getSuccessors());
        if (hasEdgeConflict(block, outgoing, targetSuccessors)) {
            return false;
        }

        removeTerminalJump(block, target);
        cfg.removeEdge(outgoing);
        for (Edge edge : targetSuccessors) {
            cfg.removeEdge(edge);
        }

        for (Instruction instruction : target.getInstructions()) {
            instruction.moveTo(block);
            block.getInstructions().add(instruction);
        }
        target.getInstructions().clear();

        for (Edge edge : targetSuccessors) {
            cfg.addEdge(edge.type, block, edge.successor);
        }
        cfg.removeDetachedBlock(target);
        return true;
    }

    private static boolean canRemoveTerminalJump(Block block, Block target) {
        List<Instruction> instructions = block.getInstructions();
        if (instructions.isEmpty()) {
            return true;
        }

        Instruction terminal = instructions.get(instructions.size() - 1);
        if (terminal instanceof Jump) {
            return ((Jump) terminal).getTarget() == target;
        }
        return !(terminal instanceof JumpIfTrue) && !(terminal instanceof JumpIfFalse);
    }

    private static void removeTerminalJump(Block block, Block target) {
        List<Instruction> instructions = block.getInstructions();
        if (instructions.isEmpty()) {
            return;
        }

        Instruction terminal = instructions.get(instructions.size() - 1);
        if (terminal instanceof Jump && ((Jump) terminal).getTarget() == target) {
            block.removeInstruction(terminal);
        }
    }

    private static boolean hasEdgeConflict(Block block, Edge outgoing, List<Edge> targetSuccessors) {
        for (Edge targetSuccessor : targetSuccessors) {
            for (Edge edge : block.getSuccessors()) {
                if (edge == outgoing) {
                    continue;
                }
                if (edge.successor == targetSuccessor.successor
                        && (edge.type == Edge.Type.THROW) != (targetSuccessor.type == Edge.Type.THROW)) {
                    return true;
                }
            }
        }
        return false;
    }
}
