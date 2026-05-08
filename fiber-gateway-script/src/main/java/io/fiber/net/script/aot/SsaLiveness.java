package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SsaLiveness {
    private final Map<SsaBlock, Set<SsaValue>> liveIn;
    private final Map<SsaBlock, Set<SsaValue>> liveOut;
    private final Map<SsaValue, Set<SsaValue>> liveAfter;
    private final Map<Edge, List<PhiMove>> edgeMoves;

    SsaLiveness(Map<SsaBlock, Set<SsaValue>> liveIn,
                Map<SsaBlock, Set<SsaValue>> liveOut,
                Map<SsaValue, Set<SsaValue>> liveAfter,
                Map<Edge, List<PhiMove>> edgeMoves) {
        this.liveIn = liveIn;
        this.liveOut = liveOut;
        this.liveAfter = liveAfter;
        this.edgeMoves = edgeMoves;
    }

    public Set<SsaValue> liveIn(SsaBlock block) {
        Set<SsaValue> values = liveIn.get(block);
        return values == null ? Collections.emptySet() : values;
    }

    public Set<SsaValue> liveOut(SsaBlock block) {
        Set<SsaValue> values = liveOut.get(block);
        return values == null ? Collections.emptySet() : values;
    }

    public Set<SsaValue> liveAfter(SsaValue value) {
        Set<SsaValue> values = liveAfter.get(value);
        return values == null ? Collections.emptySet() : values;
    }

    public List<PhiMove> edgeMoves(Edge edge) {
        List<PhiMove> moves = edgeMoves.get(edge);
        return moves == null ? Collections.emptyList() : moves;
    }

    public Map<Edge, List<PhiMove>> edgeMoves() {
        return Collections.unmodifiableMap(edgeMoves);
    }

    static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
    }

    public static final class PhiMove {
        private final SsaValue from;
        private final SsaValue to;

        PhiMove(SsaValue from, SsaValue to) {
            this.from = from;
            this.to = to;
        }

        public SsaValue from() {
            return from;
        }

        public SsaValue to() {
            return to;
        }

        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }
}
