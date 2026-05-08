package io.fiber.net.script.aot;

import java.util.Collections;
import java.util.Map;

public final class SsaStoragePlanner {
    public StoragePlan plan(SsaGraph graph, SsaLiveness liveness, AsyncPlan asyncPlan) {
        Map<SsaValue, StoragePlan.Kind> kinds = StoragePlan.identityMap();
        Map<SsaValue, Integer> locals = StoragePlan.identityMap();
        Map<SsaValue, String> fields = StoragePlan.identityMap();
        int[] nextLocal = new int[]{1};
        int[] nextField = new int[]{0};

        for (SsaBlock block : graph.blocks()) {
            assign(block.exceptionValue(), asyncPlan, kinds, locals, fields, nextLocal, nextField);
            for (SsaValue phi : block.phis()) {
                assign(phi, asyncPlan, kinds, locals, fields, nextLocal, nextField);
            }
            for (SsaValue value : block.instructions()) {
                assign(value, asyncPlan, kinds, locals, fields, nextLocal, nextField);
                if (value.bytecode() == null) {
                    continue;
                }
                for (SsaExceptionInput input : block.exceptionInputsAt(value.bytecode().pc() - block.basicBlock().startPc())) {
                    assign(input.exception(), asyncPlan, kinds, locals, fields, nextLocal, nextField);
                    VarState incoming = input.vars();
                    for (int i = 0; i < incoming.size(); i++) {
                        assign(incoming.get(i), asyncPlan, kinds, locals, fields, nextLocal, nextField);
                    }
                }
            }
            for (Edge edge : block.basicBlock().successors()) {
                for (SsaLiveness.PhiMove move : liveness.edgeMoves(edge)) {
                    assign(move.from(), asyncPlan, kinds, locals, fields, nextLocal, nextField);
                    assign(move.to(), asyncPlan, kinds, locals, fields, nextLocal, nextField);
                }
            }
        }
        return new StoragePlan(Collections.unmodifiableMap(kinds), Collections.unmodifiableMap(locals),
                Collections.unmodifiableMap(fields), nextLocal[0]);
    }

    private static void assign(SsaValue value,
                               AsyncPlan asyncPlan,
                               Map<SsaValue, StoragePlan.Kind> kinds,
                               Map<SsaValue, Integer> locals,
                               Map<SsaValue, String> fields,
                               int[] nextLocal,
                               int[] nextField) {
        if (value == null || kinds.containsKey(value)) {
            return;
        }
        if (asyncPlan.fieldValues().contains(value)) {
            kinds.put(value, StoragePlan.Kind.FIELD);
            fields.put(value, "_ssa_field_" + nextField[0]++);
            return;
        }
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case UNDEFINED:
                kinds.put(value, StoragePlan.Kind.CONST);
                return;
            case NOOP:
            case POP:
            case JUMP:
            case BRANCH:
            case RETURN:
            case THROW:
                kinds.put(value, StoragePlan.Kind.NONE);
                return;
            default:
                kinds.put(value, StoragePlan.Kind.FIELD);
                fields.put(value, "_ssa_field_" + nextField[0]++);
                break;
        }
    }
}
