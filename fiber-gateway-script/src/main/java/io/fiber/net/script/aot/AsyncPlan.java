package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class AsyncPlan {
    private final Map<SsaValue, AsyncPoint> points;
    private final Set<SsaValue> fieldValues;

    AsyncPlan(Map<SsaValue, AsyncPoint> points, Set<SsaValue> fieldValues) {
        this.points = points;
        this.fieldValues = fieldValues;
    }

    public boolean isAsyncPoint(SsaValue value) {
        return points.containsKey(value);
    }

    public AsyncPoint point(SsaValue value) {
        return points.get(value);
    }

    public Map<SsaValue, AsyncPoint> points() {
        return Collections.unmodifiableMap(points);
    }

    public Set<SsaValue> fieldValues() {
        return fieldValues;
    }

    static Set<SsaValue> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
    }

    public static final class AsyncPoint {
        private final SsaValue value;
        private final int resumeId;
        private final Set<SsaValue> liveAcross;

        AsyncPoint(SsaValue value, int resumeId, Set<SsaValue> liveAcross) {
            this.value = value;
            this.resumeId = resumeId;
            this.liveAcross = liveAcross;
        }

        public SsaValue value() {
            return value;
        }

        public int resumeId() {
            return resumeId;
        }

        public Set<SsaValue> liveAcross() {
            return liveAcross;
        }
    }
}
