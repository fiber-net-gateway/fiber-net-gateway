package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class StoragePlan {
    public enum Kind {
        NONE,
        CONST,
        LOCAL,
        FIELD
    }

    private final Map<SsaValue, Kind> kinds;
    private final Map<SsaValue, Integer> locals;
    private final Map<SsaValue, String> fields;
    private final int localCount;

    StoragePlan(Map<SsaValue, Kind> kinds, Map<SsaValue, Integer> locals, Map<SsaValue, String> fields, int localCount) {
        this.kinds = kinds;
        this.locals = locals;
        this.fields = fields;
        this.localCount = localCount;
    }

    public Kind kind(SsaValue value) {
        Kind kind = kinds.get(value);
        return kind == null ? Kind.NONE : kind;
    }

    public int local(SsaValue value) {
        Integer slot = locals.get(value);
        if (slot == null) {
            throw new SsaException("ssa value has no local slot: " + value);
        }
        return slot;
    }

    public String field(SsaValue value) {
        String field = fields.get(value);
        if (field == null) {
            throw new SsaException("ssa value has no field: " + value);
        }
        return field;
    }

    public int localCount() {
        return localCount;
    }

    public Map<SsaValue, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    public Map<SsaValue, Integer> locals() {
        return Collections.unmodifiableMap(locals);
    }

    static <T> Map<SsaValue, T> identityMap() {
        return new IdentityHashMap<>();
    }
}
