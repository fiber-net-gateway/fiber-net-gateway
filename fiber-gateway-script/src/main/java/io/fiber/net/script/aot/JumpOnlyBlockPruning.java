package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.List;

public class JumpOnlyBlockPruning {

    private final Cfg cfg;

    public JumpOnlyBlockPruning(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            if (!cfg.getBlocks().contains(block)) {
                continue;
            }
            changed |= tryPrune(block);
        }
        return changed;
    }

    private boolean tryPrune(Block block) {
        if (block == cfg.getEntryBlock()
                || !block.getPhiValues().isEmpty()
                || block.getInstructions().size() != 1
                || !(block.getInstructions().get(0) instanceof Jump)
                || block.getSuccessors().size() != 1) {
            return false;
        }

        Edge outgoing = block.getSuccessors().get(0);
        if (outgoing.type == Edge.Type.THROW) {
            return false;
        }

        Block target = ((Jump) block.getInstructions().get(0)).getTarget();
        if (target != outgoing.successor || target == block) {
            return false;
        }

        List<Edge> incomingEdges = new ArrayList<>(block.getPredecessors());
        if (incomingEdges.isEmpty() || !canReconnect(incomingEdges, block, target)) {
            return false;
        }

        List<PhiUpdate> phiUpdates = collectPhiUpdates(block, target, incomingEdges);
        if (phiUpdates == null) {
            return false;
        }

        for (PhiUpdate update : phiUpdates) {
            update.apply();
        }
        for (Edge incoming : incomingEdges) {
            retargetTerminalJump(incoming.predecessor, block, target);
            cfg.addEdge(incoming.type, incoming.predecessor, target);
        }
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

    private static List<PhiUpdate> collectPhiUpdates(Block block, Block target, List<Edge> incomingEdges) {
        List<PhiUpdate> updates = new ArrayList<>();
        for (Phi phi : target.getPhiValues()) {
            Phi.Case blockCase = findSingleCase(phi, block);
            if (blockCase == null) {
                continue;
            }
            List<Phi.Case> replacements = new ArrayList<>(incomingEdges.size());
            for (Edge incoming : incomingEdges) {
                replacements.add(new Phi.Case(incoming.predecessor, blockCase.value));
            }
            updates.add(new PhiUpdate(phi, block, replacements));
        }
        return updates;
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

    private static boolean hasEdgeTo(Block predecessor, Block successor) {
        for (Edge edge : predecessor.getSuccessors()) {
            if (edge.successor == successor) {
                return true;
            }
        }
        return false;
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
}
