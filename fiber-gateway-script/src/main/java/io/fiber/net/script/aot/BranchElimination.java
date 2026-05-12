package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.run.Compares;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class BranchElimination {

    private final Cfg cfg;

    public BranchElimination(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = foldConstantBranches();
        return removeUnreachableBlocks() || changed;
    }

    private boolean foldConstantBranches() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                Boolean jumpTaken = constantJumpDecision(instruction);
                if (jumpTaken == null) {
                    continue;
                }
                Edge kept = findKeptEdge(block, instruction, jumpTaken);
                if (kept == null) {
                    continue;
                }
                for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                    if (edge != kept) {
                        cfg.removeEdge(edge);
                    }
                }
                instruction.dropOperands();
                if (jumpTaken) {
                    block.replaceInstruction(instruction, new Jump(block, instruction.getPc(), kept.successor));
                } else {
                    block.removeInstruction(instruction);
                }
                changed = true;
            }
        }
        return changed;
    }

    private Boolean constantJumpDecision(Instruction instruction) {
        if (instruction instanceof JumpIfTrue) {
            ValueNode value = constantValue(((JumpIfTrue) instruction).getCond());
            return value == null ? null : Compares.logic(value);
        }
        if (instruction instanceof JumpIfFalse) {
            ValueNode value = constantValue(((JumpIfFalse) instruction).getCond());
            return value == null ? null : !Compares.logic(value);
        }
        return null;
    }

    private static ValueNode constantValue(SsaValue value) {
        Expr assign = value.getAssign();
        return assign instanceof LoadConst ? ((LoadConst) assign).getValueNode() : null;
    }

    private static Edge findKeptEdge(Block block, Instruction instruction, boolean jumpTaken) {
        Block target = instruction instanceof JumpIfTrue
                ? ((JumpIfTrue) instruction).getTarget()
                : ((JumpIfFalse) instruction).getTarget();
        for (Edge edge : block.getSuccessors()) {
            if (jumpTaken) {
                if (edge.type == Edge.Type.JUMP && edge.successor == target) {
                    return edge;
                }
            } else if (edge.type == Edge.Type.FALLTHROUGH) {
                return edge;
            }
        }
        return null;
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
