package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AsyncSpillAnalysis {

    private final Cfg cfg;
    private final LivenessAnalysis.Result liveness;

    public AsyncSpillAnalysis(Cfg cfg) {
        this(cfg, new LivenessAnalysis(cfg).analyze());
    }

    public AsyncSpillAnalysis(Cfg cfg, LivenessAnalysis.Result liveness) {
        this.cfg = cfg;
        this.liveness = liveness;
    }

    public Result analyze() {
        Map<Instruction, Set<SsaValue>> valuesByAsync = new IdentityHashMap<>();
        Map<SsaValue, Integer> spillIds = new IdentityHashMap<>();
        Set<SsaValue> allSpills = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        List<SsaValue> spillValues = new ArrayList<>();
        List<Instruction> asyncInstructions = new ArrayList<>();

        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (!isAsyncCall(instruction)) {
                    continue;
                }
                asyncInstructions.add(instruction);

                Set<SsaValue> values = newSet(liveness.liveAfter(instruction));
                if (instruction instanceof Expr) {
                    values.remove(((Expr) instruction).getResult());
                }
                valuesByAsync.put(instruction, Collections.unmodifiableSet(values));
                allSpills.addAll(values);
            }
        }

        assignSpillIds(allSpills, spillIds, spillValues);
        return new Result(valuesByAsync, spillIds, spillValues, asyncInstructions);
    }

    static boolean isAsyncCall(Instruction instruction) {
        return instruction instanceof CallAsyncConst || instruction instanceof CallAsyncFunc;
    }

    private void assignSpillIds(Set<SsaValue> allSpills, Map<SsaValue, Integer> spillIds, List<SsaValue> spillValues) {
        for (Block block : cfg.getBlocks()) {
            for (Phi phi : block.getPhiValues()) {
                assignSpillId(phi.getResult(), allSpills, spillIds, spillValues);
            }
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Expr) {
                    assignSpillId(((Expr) instruction).getResult(), allSpills, spillIds, spillValues);
                }
            }
        }
    }

    private static void assignSpillId(SsaValue value,
                                      Set<SsaValue> allSpills,
                                      Map<SsaValue, Integer> spillIds,
                                      List<SsaValue> spillValues) {
        if (!allSpills.contains(value) || spillIds.containsKey(value)) {
            return;
        }
        spillIds.put(value, spillValues.size());
        spillValues.add(value);
    }

    private static Set<SsaValue> newSet(Iterable<SsaValue> values) {
        Set<SsaValue> set = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        for (SsaValue value : values) {
            set.add(value);
        }
        return set;
    }

    public static class Result {
        private final Map<Instruction, Set<SsaValue>> valuesByAsync;
        private final Map<SsaValue, Integer> spillIds;
        private final List<SsaValue> spillValues;
        private final List<Instruction> asyncInstructions;

        private Result(Map<Instruction, Set<SsaValue>> valuesByAsync,
                       Map<SsaValue, Integer> spillIds,
                       List<SsaValue> spillValues,
                       List<Instruction> asyncInstructions) {
            this.valuesByAsync = new IdentityHashMap<>(valuesByAsync);
            this.spillIds = new IdentityHashMap<>(spillIds);
            this.spillValues = Collections.unmodifiableList(new ArrayList<>(spillValues));
            this.asyncInstructions = Collections.unmodifiableList(new ArrayList<>(asyncInstructions));
        }

        public List<Instruction> getAsyncInstructions() {
            return asyncInstructions;
        }

        public Set<SsaValue> getSpillValues(Instruction asyncInstruction) {
            Set<SsaValue> values = valuesByAsync.get(asyncInstruction);
            return values == null ? Collections.<SsaValue>emptySet() : values;
        }

        public List<SsaValue> getSpillValues() {
            return spillValues;
        }

        public boolean isSpilled(SsaValue value) {
            return spillIds.containsKey(value);
        }

        public int getSpillId(SsaValue value) {
            Integer id = spillIds.get(value);
            return id == null ? -1 : id;
        }
    }
}
