package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class LoopInvariantCodeMotion {

    private final Cfg cfg;
    private Dominators dominators;
    private boolean changed;

    public LoopInvariantCodeMotion(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        dominators = Dominators.compute(cfg);
        for (Block block : dominators.reversePostOrder) {
            for (Edge edge : block.getSuccessors()) {
                if (edge.type == Edge.Type.THROW || !dominators.dominates(edge.successor, block)) {
                    continue;
                }
                optimizeLoop(edge.successor, block);
            }
        }
        return changed;
    }

    private void optimizeLoop(Block header, Block latch) {
        Set<Block> loopBlocks = findNaturalLoop(header, latch);
        Block preheader = findPreheader(header, loopBlocks);
        if (preheader == null) {
            return;
        }
        int insertAt = insertionPoint(preheader, header);
        if (insertAt < 0) {
            return;
        }

        Set<SsaValue> loopDefined = collectLoopDefined(loopBlocks);
        Set<SsaValue> invariantValues = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        List<Expr> invariantExprs = new ArrayList<>();

        boolean found;
        do {
            found = false;
            for (Block block : dominators.reversePostOrder) {
                if (!loopBlocks.contains(block)) {
                    continue;
                }
                for (Instruction instruction : block.getInstructions()) {
                    if (!(instruction instanceof Expr)) {
                        continue;
                    }
                    Expr expr = (Expr) instruction;
                    if (!isCandidateShape(expr) || invariantValues.contains(expr.getResult())) {
                        continue;
                    }
                    if (!areOperandsInvariant(expr, loopDefined, invariantValues)) {
                        continue;
                    }
                    if (expr.canThrow() != Instruction.Throw.NOT) {
                        continue;
                    }
                    invariantValues.add(expr.getResult());
                    invariantExprs.add(expr);
                    found = true;
                }
            }
        } while (found);

        for (Expr expr : invariantExprs) {
            Block source = expr.getBelongTo();
            if (source == preheader || !source.getInstructions().remove(expr)) {
                continue;
            }
            expr.moveTo(preheader);
            preheader.getInstructions().add(insertAt++, expr);
            changed = true;
        }
    }

    private Set<Block> findNaturalLoop(Block header, Block latch) {
        Set<Block> loopBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        ArrayDeque<Block> queue = new ArrayDeque<>();
        loopBlocks.add(header);
        loopBlocks.add(latch);
        queue.add(latch);
        while (!queue.isEmpty()) {
            Block block = queue.poll();
            for (Edge edge : block.getPredecessors()) {
                if (edge.type == Edge.Type.THROW) {
                    continue;
                }
                Block predecessor = edge.predecessor;
                if (loopBlocks.add(predecessor) && predecessor != header) {
                    queue.add(predecessor);
                }
            }
        }
        return loopBlocks;
    }

    private Block findPreheader(Block header, Set<Block> loopBlocks) {
        Block preheader = null;
        for (Edge edge : header.getPredecessors()) {
            if (edge.type == Edge.Type.THROW || loopBlocks.contains(edge.predecessor)) {
                continue;
            }
            if (preheader != null) {
                return null;
            }
            preheader = edge.predecessor;
        }
        return preheader;
    }

    private int insertionPoint(Block preheader, Block header) {
        List<Instruction> instructions = preheader.getInstructions();
        if (instructions.isEmpty()) {
            return singleNormalSuccessor(preheader, header) ? 0 : -1;
        }
        Instruction last = instructions.get(instructions.size() - 1);
        if (last instanceof Jump) {
            return ((Jump) last).getTarget() == header ? instructions.size() - 1 : -1;
        }
        return singleNormalSuccessor(preheader, header) && last.canThrow() == Instruction.Throw.NOT
                ? instructions.size()
                : -1;
    }

    private boolean singleNormalSuccessor(Block block, Block successor) {
        int count = 0;
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.THROW) {
                continue;
            }
            if (edge.successor != successor) {
                return false;
            }
            count++;
        }
        return count == 1;
    }

    private static Set<SsaValue> collectLoopDefined(Set<Block> loopBlocks) {
        Set<SsaValue> values = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        for (Block block : loopBlocks) {
            for (Phi phi : block.getPhiValues()) {
                values.add(phi.getResult());
            }
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Expr) {
                    values.add(((Expr) instruction).getResult());
                }
            }
        }
        return values;
    }

    private static boolean isCandidateShape(Expr expr) {
        if (expr.getResult().getUsedCount() == 0) {
            return false;
        }
        return expr instanceof LoadConst
                || expr instanceof LoadRoot
                || expr instanceof Binary
                || expr instanceof Unary && isPureUnary((Unary) expr);
    }

    private static boolean isPureUnary(Unary unary) {
        switch (unary.getOp()) {
            case PLUS:
            case MINUS:
            case NEG:
            case TYPEOF:
                return true;
            default:
                return false;
        }
    }

    private static boolean areOperandsInvariant(Expr expr,
                                                Set<SsaValue> loopDefined,
                                                Set<SsaValue> invariantValues) {
        if (expr instanceof LoadConst || expr instanceof LoadRoot) {
            return true;
        }
        if (expr instanceof Unary) {
            return isInvariant(((Unary) expr).getMaterial(), loopDefined, invariantValues);
        }
        Binary binary = (Binary) expr;
        return isInvariant(binary.getLeft(), loopDefined, invariantValues)
                && isInvariant(binary.getRight(), loopDefined, invariantValues);
    }

    private static boolean isInvariant(SsaValue value,
                                       Set<SsaValue> loopDefined,
                                       Set<SsaValue> invariantValues) {
        return !loopDefined.contains(value) || invariantValues.contains(value);
    }
}
