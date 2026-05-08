package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SsaLivenessAnalyzer {
    public SsaLiveness analyze(SsaGraph graph) {
        Map<SsaBlock, BlockUseDef> useDefs = computeUseDefs(graph);
        Map<Edge, List<SsaLiveness.PhiMove>> edgeMoves = computeEdgeMoves(graph);
        Map<SsaBlock, Set<SsaValue>> liveIn = new IdentityHashMap<>();
        Map<SsaBlock, Set<SsaValue>> liveOut = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            liveIn.put(block, SsaLiveness.<SsaValue>identitySet());
            liveOut.put(block, SsaLiveness.<SsaValue>identitySet());
        }

        boolean changed;
        do {
            changed = false;
            List<SsaBlock> blocks = graph.blocks();
            for (int i = blocks.size() - 1; i >= 0; i--) {
                SsaBlock block = blocks.get(i);
                Set<SsaValue> nextOut = SsaLiveness.identitySet();
                for (Edge edge : block.basicBlock().successors()) {
                    SsaBlock to = graph.blockFor(edge.to());
                    if (to == null) {
                        continue;
                    }
                    nextOut.addAll(liveIn.get(to));
                    for (SsaLiveness.PhiMove move : edgeMoves(edgeMoves, edge)) {
                        if (!move.to().uses().isEmpty()) {
                            nextOut.add(move.from());
                        }
                    }
                }

                Set<SsaValue> nextIn = SsaLiveness.identitySet();
                nextIn.addAll(nextOut);
                nextIn.removeAll(useDefs.get(block).defs);
                nextIn.addAll(useDefs.get(block).uses);

                changed |= replaceIfChanged(liveOut, block, nextOut);
                changed |= replaceIfChanged(liveIn, block, nextIn);
            }
        } while (changed);

        Map<SsaValue, Set<SsaValue>> liveAfter = computeLiveAfter(graph, liveOut);
        return new SsaLiveness(freeze(liveIn), freeze(liveOut), freeze(liveAfter), freezeMoves(edgeMoves));
    }

    private static Map<SsaBlock, BlockUseDef> computeUseDefs(SsaGraph graph) {
        Map<SsaBlock, BlockUseDef> output = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            Set<SsaValue> defs = SsaLiveness.identitySet();
            Set<SsaValue> uses = SsaLiveness.identitySet();
            defs.addAll(block.phis());
            for (SsaValue value : block.instructions()) {
                addUses(value, defs, uses);
                defs.add(value);
            }
            output.put(block, new BlockUseDef(uses, defs));
        }
        return output;
    }

    private static void addUses(SsaValue value, Set<SsaValue> defs, Set<SsaValue> uses) {
        for (SsaValue operand : value.operands()) {
            if (!defs.contains(operand)) {
                uses.add(operand);
            }
        }
    }

    private static Map<Edge, List<SsaLiveness.PhiMove>> computeEdgeMoves(SsaGraph graph) {
        Map<Edge, List<SsaLiveness.PhiMove>> moves = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            for (Edge edge : block.basicBlock().successors()) {
                SsaBlock target = graph.blockFor(edge.to());
                if (target == null || target.phis().isEmpty()) {
                    continue;
                }
                ArrayList<SsaLiveness.PhiMove> edgeMoves = new ArrayList<>();
                for (SsaValue phi : target.phis()) {
                    SsaValue incoming = incomingValue(block, target, phi);
                    if (incoming != null && incoming != phi) {
                        addMove(edgeMoves, incoming, phi);
                    }
                }
                addStateMoves(edgeMoves, block.outStack(), target.inStack());
                addStateMoves(edgeMoves, block.outVars(), target.inVars());
                if (!edgeMoves.isEmpty()) {
                    moves.put(edge, Collections.unmodifiableList(edgeMoves));
                }
            }
        }
        return moves;
    }

    private static void addStateMoves(ArrayList<SsaLiveness.PhiMove> moves, StackState from, StackState to) {
        if (from == null || to == null || from.size() != to.size()) {
            return;
        }
        for (int i = 0; i < to.size(); i++) {
            addMove(moves, from.get(i), to.get(i));
        }
    }

    private static void addStateMoves(ArrayList<SsaLiveness.PhiMove> moves, VarState from, VarState to) {
        if (from == null || to == null || from.size() != to.size()) {
            return;
        }
        for (int i = 0; i < to.size(); i++) {
            addMove(moves, from.get(i), to.get(i));
        }
    }

    private static void addMove(ArrayList<SsaLiveness.PhiMove> moves, SsaValue from, SsaValue to) {
        if (from == null || to == null || from == to || !needsStorageMove(to)) {
            return;
        }
        for (SsaLiveness.PhiMove move : moves) {
            if (move.from() == from && move.to() == to) {
                return;
            }
        }
        moves.add(new SsaLiveness.PhiMove(from, to));
    }

    private static boolean needsStorageMove(SsaValue value) {
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case UNDEFINED:
            case NOOP:
            case POP:
            case JUMP:
            case BRANCH:
            case RETURN:
            case THROW:
                return false;
            default:
                return true;
        }
    }

    private static SsaValue incomingValue(SsaBlock from, SsaBlock to, SsaValue phi) {
        StackState inStack = to.inStack();
        if (inStack != null && phi.index() >= 0 && phi.index() < inStack.size() && inStack.get(phi.index()) == phi) {
            StackState outStack = from.outStack();
            return outStack == null || phi.index() >= outStack.size() ? null : outStack.get(phi.index());
        }
        VarState inVars = to.inVars();
        if (inVars != null && phi.index() >= 0 && phi.index() < inVars.size() && inVars.get(phi.index()) == phi) {
            VarState outVars = from.outVars();
            return outVars == null || phi.index() >= outVars.size() ? null : outVars.get(phi.index());
        }
        SsaValue exception = to.exceptionValue();
        if (exception == phi) {
            return from.exceptionValue();
        }
        return null;
    }

    private static Map<SsaValue, Set<SsaValue>> computeLiveAfter(SsaGraph graph, Map<SsaBlock, Set<SsaValue>> liveOut) {
        Map<SsaValue, Set<SsaValue>> output = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            Set<SsaValue> live = SsaLiveness.identitySet();
            live.addAll(liveOut.get(block));
            List<SsaValue> instructions = block.instructions();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                SsaValue value = instructions.get(i);
                output.put(value, immutableIdentityCopy(live));
                live.remove(value);
                live.addAll(value.operands());
            }
        }
        return output;
    }

    private static boolean replaceIfChanged(Map<SsaBlock, Set<SsaValue>> map, SsaBlock block, Set<SsaValue> next) {
        Set<SsaValue> old = map.get(block);
        if (old.equals(next)) {
            return false;
        }
        map.put(block, next);
        return true;
    }

    private static List<SsaLiveness.PhiMove> edgeMoves(Map<Edge, List<SsaLiveness.PhiMove>> moves, Edge edge) {
        List<SsaLiveness.PhiMove> list = moves.get(edge);
        return list == null ? Collections.emptyList() : list;
    }

    private static <K> Map<K, Set<SsaValue>> freeze(Map<K, Set<SsaValue>> input) {
        Map<K, Set<SsaValue>> output = new IdentityHashMap<>();
        for (Map.Entry<K, Set<SsaValue>> entry : input.entrySet()) {
            output.put(entry.getKey(), immutableIdentityCopy(entry.getValue()));
        }
        return Collections.unmodifiableMap(output);
    }

    private static Map<Edge, List<SsaLiveness.PhiMove>> freezeMoves(Map<Edge, List<SsaLiveness.PhiMove>> input) {
        Map<Edge, List<SsaLiveness.PhiMove>> output = new IdentityHashMap<>();
        output.putAll(input);
        return Collections.unmodifiableMap(output);
    }

    private static Set<SsaValue> immutableIdentityCopy(Set<SsaValue> values) {
        Set<SsaValue> copy = SsaLiveness.identitySet();
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }

    private static final class BlockUseDef {
        private final Set<SsaValue> uses;
        private final Set<SsaValue> defs;

        private BlockUseDef(Set<SsaValue> uses, Set<SsaValue> defs) {
            this.uses = uses;
            this.defs = defs;
        }
    }
}
