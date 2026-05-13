package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SsaDestruction {

    private final Cfg cfg;

    public SsaDestruction(Cfg cfg) {
        this.cfg = cfg;
    }

    public Result analyze() {
        List<EdgeCopy> edgeCopies = new ArrayList<>();
        Map<Edge, EdgeCopy> copiesByEdge = new IdentityHashMap<>();
        Set<Block> phiBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());

        for (Block block : cfg.getBlocks()) {
            List<Phi> phis = block.getPhiValues();
            if (phis.isEmpty()) {
                continue;
            }
            phiBlocks.add(block);
            for (Edge edge : block.getPredecessors()) {
                List<Move> moves = collectMoves(edge, phis);
                if (moves.isEmpty()) {
                    continue;
                }
                EdgeCopy edgeCopy = new EdgeCopy(edge, moves, needsVirtualBlock(edge));
                edgeCopies.add(edgeCopy);
                copiesByEdge.put(edge, edgeCopy);
            }
        }

        return new Result(edgeCopies, copiesByEdge, phiBlocks);
    }

    private static List<Move> collectMoves(Edge edge, List<Phi> phis) {
        List<Move> moves = new ArrayList<>(phis.size());
        for (Phi phi : phis) {
            SsaValue src = incomingValue(edge, phi);
            SsaValue dst = phi.getResult();
            if (src != dst) {
                moves.add(new Move(dst, src));
            }
        }
        return moves;
    }

    private static SsaValue incomingValue(Edge edge, Phi phi) {
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.from == edge.predecessor) {
                return aCase.value;
            }
        }
        throw new IllegalStateException("[bug] missing phi case from predecessor " + edge.predecessor.startPc +
                " to block " + edge.successor.startPc);
    }

    private static boolean needsVirtualBlock(Edge edge) {
        if (edge.type == Edge.Type.THROW) {
            return true;
        }
        int normalSuccessors = 0;
        for (Edge successor : edge.predecessor.getSuccessors()) {
            if (successor.type != Edge.Type.THROW) {
                normalSuccessors++;
            }
        }
        return normalSuccessors > 1;
    }

    public static class Move {
        private final SsaValue dst;
        private final SsaValue src;

        private Move(SsaValue dst, SsaValue src) {
            this.dst = dst;
            this.src = src;
        }

        public SsaValue getDst() {
            return dst;
        }

        public SsaValue getSrc() {
            return src;
        }
    }

    public static class EdgeCopy {
        private final Edge edge;
        private final List<Move> moves;
        private final boolean needsVirtualBlock;

        private EdgeCopy(Edge edge, List<Move> moves, boolean needsVirtualBlock) {
            this.edge = edge;
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
            this.needsVirtualBlock = needsVirtualBlock;
        }

        public Edge getEdge() {
            return edge;
        }

        public List<Move> getMoves() {
            return moves;
        }

        public boolean needsVirtualBlock() {
            return needsVirtualBlock;
        }
    }

    public static class Result {
        private final List<EdgeCopy> edgeCopies;
        private final Map<Edge, EdgeCopy> copiesByEdge;
        private final Set<Block> phiBlocks;

        private Result(List<EdgeCopy> edgeCopies, Map<Edge, EdgeCopy> copiesByEdge, Set<Block> phiBlocks) {
            this.edgeCopies = Collections.unmodifiableList(new ArrayList<>(edgeCopies));
            this.copiesByEdge = new IdentityHashMap<>(copiesByEdge);
            Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
            blocks.addAll(phiBlocks);
            this.phiBlocks = Collections.unmodifiableSet(blocks);
        }

        public List<EdgeCopy> getEdgeCopies() {
            return edgeCopies;
        }

        public EdgeCopy getEdgeCopy(Edge edge) {
            return copiesByEdge.get(edge);
        }

        public boolean hasCopies(Edge edge) {
            return copiesByEdge.containsKey(edge);
        }

        public Set<Block> getPhiBlocks() {
            return phiBlocks;
        }

        public boolean hasPhi(Block block) {
            return phiBlocks.contains(block);
        }
    }
}
