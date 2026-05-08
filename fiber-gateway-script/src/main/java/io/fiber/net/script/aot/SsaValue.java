package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNodeType;
import io.fiber.net.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SsaValue {
    public enum Type {
        ARRAY,
        BINARY,
        BOOLEAN,
        MISSING,
        NULL,
        NUMBER,
        OBJECT,
        STRING,
        // for script
        EXCEPTION,
        ITERATOR,
        Unknown,
    }

    private static final Type[] VALUES = Type.values();

    public static Type ofType(JsonNodeType v) {
        if (v == null) {
            return Type.Unknown;
        }
        return VALUES[v.ordinal()];
    }

    private final Expr assign;
    private List<Instruction> used;

    public SsaValue(Expr assign) {
        this.assign = assign;
    }

    public int getUsedCount() {
        if (CollectionUtils.isEmpty(used)) {
            return 0;
        }
        return used.size();
    }

    public Expr getAssign() {
        return assign;
    }

    public void addUsed(Instruction usedIns) {
        if (used == null) {
            used = new ArrayList<>();
        }
        used.add(usedIns);
    }

    public List<Instruction> getUsed() {
        if (used == null) {
            return Collections.emptyList();
        }
        return used;
    }

    public Type getType() {
        return assign.getResultType();
    }
}
