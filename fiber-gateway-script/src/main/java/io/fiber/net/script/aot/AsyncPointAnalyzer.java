package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class AsyncPointAnalyzer {
    public AsyncPlan analyze(SsaGraph graph, SsaLiveness liveness) {
        Map<SsaValue, AsyncPlan.AsyncPoint> points = new IdentityHashMap<>();
        Set<SsaValue> fieldValues = AsyncPlan.identitySet();
        int resumeId = 1;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (!isAsync(value)) {
                    continue;
                }
                Set<SsaValue> liveAcross = liveAcross(value, liveness);
                fieldValues.addAll(liveAcross);
                points.put(value, new AsyncPlan.AsyncPoint(value, resumeId++, liveAcross));
            }
        }
        return new AsyncPlan(Collections.unmodifiableMap(points), immutableIdentitySet(fieldValues));
    }

    private static boolean isAsync(SsaValue value) {
        return value.kind() == SsaKind.ASYNC_CALL || value.kind() == SsaKind.ASYNC_CONST_CALL;
    }

    private static Set<SsaValue> liveAcross(SsaValue asyncValue, SsaLiveness liveness) {
        Set<SsaValue> live = AsyncPlan.identitySet();
        for (SsaValue value : liveness.liveAfter(asyncValue)) {
            if (value != asyncValue && needsMaterializedFrame(value)) {
                live.add(value);
            }
        }
        return immutableIdentitySet(live);
    }

    private static boolean needsMaterializedFrame(SsaValue value) {
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case UNDEFINED:
            case NOOP:
                return false;
            default:
                return true;
        }
    }

    private static Set<SsaValue> immutableIdentitySet(Set<SsaValue> values) {
        Set<SsaValue> copy = AsyncPlan.identitySet();
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }
}
