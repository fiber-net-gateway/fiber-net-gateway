package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class EmptyBlockPruning {

    private final Cfg cfg;

    public EmptyBlockPruning(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            changed |= tryPrune(block);
        }
        return changed;
    }

    private boolean tryPrune(Block block) {
        if (!block.getInstructions().isEmpty() || block.getSuccessors().size() != 1) {
            return false;
        }

        Edge outgoing = block.getSuccessors().get(0);
        if (outgoing.type == Edge.Type.THROW) {
            return false;
        }

        Block target = outgoing.successor;
        if (block == cfg.getEntryBlock()) {
            return tryMoveEntry(block, target);
        }

        List<Edge> incomingEdges = new ArrayList<>(block.getPredecessors());
        if (incomingEdges.isEmpty() || !canReconnect(incomingEdges, block, target)) {
            return false;
        }

        List<PhiUpdate> phiUpdates = collectPhiUpdates(block, target, incomingEdges);
        if (phiUpdates == null) {
            return false;
        }

        List<PhiMove> phiMoves = collectPhiMoves(block, target, incomingEdges);
        if (phiMoves == null) {
            return false;
        }

        for (PhiUpdate update : phiUpdates) {
            update.apply();
        }
        for (PhiMove move : phiMoves) {
            move.apply();
        }
        for (Edge incoming : incomingEdges) {
            cfg.addEdge(incoming.type, incoming.predecessor, target);
        }
        cfg.removeBlock(block);
        return true;
    }

    private boolean tryMoveEntry(Block block, Block target) {
        if (!block.getPhiValues().isEmpty()
                || !target.getPhiValues().isEmpty()
                || target.getPredecessors().size() != 1
                || target.getPredecessors().get(0).predecessor != block) {
            return false;
        }
        cfg.setEntryBlock(target);
        cfg.removeBlock(block);
        return true;
    }

    private static boolean canReconnect(List<Edge> incomingEdges, Block block, Block target) {
        for (Edge incoming : incomingEdges) {
            if (incoming.type == Edge.Type.THROW
                    || incoming.predecessor == block
                    || incoming.predecessor == target
                    || hasEdgeTo(incoming.predecessor, target)) {
                return false;
            }
        }
        return true;
    }

    private List<PhiUpdate> collectPhiUpdates(Block block, Block target, List<Edge> incomingEdges) {
        List<PhiUpdate> updates = new ArrayList<>();
        for (Phi phi : target.getPhiValues()) {
            Phi.Case blockCase = findSingleCase(phi, block);
            if (blockCase == null) {
                continue;
            }

            List<Phi.Case> replacements = new ArrayList<>(incomingEdges.size());
            for (Edge incoming : incomingEdges) {
                SsaValue value = resolveThrough(block, incoming.predecessor, blockCase.value);
                if (value == null) {
                    return null;
                }
                replacements.add(new Phi.Case(incoming.predecessor, value));
            }
            updates.add(new PhiUpdate(phi, block, replacements));
        }
        return updates;
    }

    private List<PhiMove> collectPhiMoves(Block block, Block target, List<Edge> incomingEdges) {
        List<PhiMove> moves = new ArrayList<>();
        for (Phi phi : block.getPhiValues()) {
            if (!hasExternalUse(phi, block, target)) {
                continue;
            }

            List<Phi.Case> replacements = new ArrayList<>(incomingEdges.size());
            for (Edge incoming : incomingEdges) {
                SsaValue value = resolveThrough(block, incoming.predecessor, phi.getResult());
                if (value == null) {
                    return null;
                }
                replacements.add(new Phi.Case(incoming.predecessor, value));
            }
            moves.add(new PhiMove(cfg, target, phi, replacements));
        }
        return moves;
    }

    private static boolean hasExternalUse(Phi phi, Block block, Block target) {
        SsaValue result = phi.getResult();
        for (Instruction used : result.getUsed()) {
            if (used instanceof Phi) {
                Phi usedPhi = (Phi) used;
                if (usedPhi.getBelongTo() == block) {
                    continue;
                }
                if (usedPhi.getBelongTo() == target && hasCase(usedPhi, block, result)) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private static SsaValue resolveThrough(Block block, Block predecessor, SsaValue value) {
        return resolveThrough(block, predecessor, value,
                Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>()));
    }

    private static SsaValue resolveThrough(Block block, Block predecessor, SsaValue value, Set<SsaValue> seen) {
        Expr assign = value.getAssign();
        if (!(assign instanceof Phi) || assign.getBelongTo() != block) {
            return value;
        }
        if (!seen.add(value)) {
            return null;
        }
        Phi phi = (Phi) assign;
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.from == predecessor) {
                return resolveThrough(block, predecessor, aCase.value, seen);
            }
        }
        return null;
    }

    private static Phi.Case findSingleCase(Phi phi, Block block) {
        Phi.Case found = null;
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.from != block) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = aCase;
        }
        return found;
    }

    private static boolean hasCase(Phi phi, Block from, SsaValue value) {
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.from == from && aCase.value == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEdgeTo(Block predecessor, Block successor) {
        for (Edge edge : predecessor.getSuccessors()) {
            if (edge.successor == successor) {
                return true;
            }
        }
        return false;
    }

    private static final class PhiUpdate {
        private final Phi phi;
        private final Block oldFrom;
        private final List<Phi.Case> replacements;

        private PhiUpdate(Phi phi, Block oldFrom, List<Phi.Case> replacements) {
            this.phi = phi;
            this.oldFrom = oldFrom;
            this.replacements = replacements;
        }

        private void apply() {
            phi.removeCase(oldFrom);
            for (Phi.Case replacement : replacements) {
                phi.addCase(replacement.from, replacement.value);
            }
        }
    }

    private static final class PhiMove {
        private final Cfg cfg;
        private final Block target;
        private final Phi oldPhi;
        private final List<Phi.Case> replacements;

        private PhiMove(Cfg cfg, Block target, Phi oldPhi, List<Phi.Case> replacements) {
            this.cfg = cfg;
            this.target = target;
            this.oldPhi = oldPhi;
            this.replacements = replacements;
        }

        private void apply() {
            Phi moved = target.newPhi();
            for (Phi.Case replacement : replacements) {
                moved.addCase(replacement.from, replacement.value);
            }
            target.addPhi(moved);
            cfg.replaceValue(oldPhi.getResult(), moved.getResult());
        }
    }
}
