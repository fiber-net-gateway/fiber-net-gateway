package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.run.Compares;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class BranchElimination {

    private final Cfg cfg;
    private final OptimizationContext context;

    public BranchElimination(Cfg cfg) {
        this(cfg, new OptimizationContext(cfg));
    }

    BranchElimination(Cfg cfg, OptimizationContext context) {
        this.cfg = cfg;
        this.context = context;
    }

    public boolean optimize() {
        boolean changed = foldConstantBranches();
        changed |= removeRedundantBranches();
        if (changed) {
            context.invalidateControlFlow();
        }
        return context.removeUnreachableBlocks() || changed;
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

    private boolean removeRedundantBranches() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (!(instruction instanceof JumpIfTrue) && !(instruction instanceof JumpIfFalse)) {
                    continue;
                }
                Edge jump = findEdge(block, Edge.Type.JUMP);
                Edge fallthrough = findEdge(block, Edge.Type.FALLTHROUGH);
                if (jump == null || fallthrough == null) {
                    continue;
                }
                Block jumpTarget = transparentTarget(jump.successor);
                Block fallthroughTarget = transparentTarget(fallthrough.successor);
                if (jumpTarget == null || jumpTarget != fallthroughTarget || !jumpTarget.getPhiValues().isEmpty()) {
                    continue;
                }
                for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                    cfg.removeEdge(edge);
                }
                cfg.addEdge(Edge.Type.FALLTHROUGH, block, jumpTarget);
                instruction.dropOperands();
                block.removeInstruction(instruction);
                changed = true;
            }
        }
        return changed;
    }

    private static Block transparentTarget(Block block) {
        Set<Block> visited = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        Block current = block;
        while (visited.add(current)) {
            if (!current.getPhiValues().isEmpty()) {
                return current;
            }
            Edge next = transparentNext(current);
            if (next == null || next.type == Edge.Type.THROW) {
                return current;
            }
            current = next.successor;
        }
        return null;
    }

    private static Edge transparentNext(Block block) {
        Edge next = null;
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.THROW) {
                return null;
            }
            if (next != null) {
                return null;
            }
            next = edge;
        }
        if (next == null) {
            return null;
        }
        for (Instruction instruction : block.getInstructions()) {
            if (instruction instanceof Jump) {
                continue;
            }
            if (!instruction.isRemovablePure()) {
                return null;
            }
        }
        return next;
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

    private static Edge findEdge(Block block, Edge.Type type) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == type) {
                return edge;
            }
        }
        return null;
    }

}
