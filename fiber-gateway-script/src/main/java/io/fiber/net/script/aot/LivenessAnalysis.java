package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LivenessAnalysis {

    private final Cfg cfg;

    public LivenessAnalysis(Cfg cfg) {
        this.cfg = cfg;
    }

    public Result analyze() {
        List<Block> blocks = new ArrayList<>(cfg.getBlocks());
        Map<Block, Set<SsaValue>> use = new IdentityHashMap<>();
        Map<Block, Set<SsaValue>> def = new IdentityHashMap<>();
        Map<Block, Set<SsaValue>> liveIn = new IdentityHashMap<>();
        Map<Block, Set<SsaValue>> liveOut = new IdentityHashMap<>();

        for (Block block : blocks) {
            Set<SsaValue> blockUse = newSet();
            Set<SsaValue> blockDef = newSet();
            for (Phi phi : block.getPhiValues()) {
                blockDef.add(phi.getResult());
            }
            for (Instruction instruction : block.getInstructions()) {
                for (SsaValue value : operandsOf(instruction)) {
                    if (!blockDef.contains(value)) {
                        blockUse.add(value);
                    }
                }
                if (instruction instanceof Expr) {
                    blockDef.add(((Expr) instruction).getResult());
                }
            }
            use.put(block, blockUse);
            def.put(block, blockDef);
            liveIn.put(block, newSet());
            liveOut.put(block, newSet());
        }

        boolean changed;
        do {
            changed = false;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                Block block = blocks.get(i);
                Set<SsaValue> nextOut = newSet();
                for (Edge edge : block.getSuccessors()) {
                    nextOut.addAll(edgeLiveIn(edge, liveIn.get(edge.successor)));
                }

                Set<SsaValue> nextIn = newSet(use.get(block));
                for (SsaValue value : nextOut) {
                    if (!def.get(block).contains(value)) {
                        nextIn.add(value);
                    }
                }

                if (!nextOut.equals(liveOut.get(block))) {
                    liveOut.put(block, nextOut);
                    changed = true;
                }
                if (!nextIn.equals(liveIn.get(block))) {
                    liveIn.put(block, nextIn);
                    changed = true;
                }
            }
        } while (changed);

        Map<Instruction, Set<SsaValue>> liveBefore = new IdentityHashMap<>();
        Map<Instruction, Set<SsaValue>> liveAfter = new IdentityHashMap<>();
        for (Block block : blocks) {
            Set<SsaValue> live = newSet(liveOut.get(block));
            List<Instruction> instructions = block.getInstructions();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instruction instruction = instructions.get(i);
                liveAfter.put(instruction, newSet(live));
                if (instruction instanceof Expr) {
                    live.remove(((Expr) instruction).getResult());
                }
                live.addAll(operandsOf(instruction));
                liveBefore.put(instruction, newSet(live));
            }
        }

        return new Result(liveIn, liveOut, liveBefore, liveAfter);
    }

    static Collection<SsaValue> operandsOf(Instruction instruction) {
        if (instruction instanceof Binary) {
            Binary binary = (Binary) instruction;
            return values(binary.getLeft(), binary.getRight());
        }
        if (instruction instanceof Unary) {
            return values(((Unary) instruction).getMaterial());
        }
        if (instruction instanceof IndexGet) {
            IndexGet indexGet = (IndexGet) instruction;
            return values(indexGet.getOwner(), indexGet.getKey());
        }
        if (instruction instanceof IndexSet) {
            IndexSet indexSet = (IndexSet) instruction;
            return values(indexSet.getOwner(), indexSet.getKey(), indexSet.getAlien());
        }
        if (instruction instanceof IndexSet1) {
            IndexSet1 indexSet = (IndexSet1) instruction;
            return values(indexSet.getOwner(), indexSet.getKey(), indexSet.getAlien());
        }
        if (instruction instanceof PropGet) {
            return values(((PropGet) instruction).getOwner());
        }
        if (instruction instanceof PropSet) {
            PropSet propSet = (PropSet) instruction;
            return values(propSet.getOwner(), propSet.getAlien());
        }
        if (instruction instanceof PropSet1) {
            PropSet1 propSet = (PropSet1) instruction;
            return values(propSet.getOwner(), propSet.getAlien());
        }
        if (instruction instanceof ExpandObj) {
            ExpandObj expandObj = (ExpandObj) instruction;
            return values(expandObj.getTarget(), expandObj.getAddition());
        }
        if (instruction instanceof ExpandArr) {
            ExpandArr expandArr = (ExpandArr) instruction;
            return values(expandArr.getTarget(), expandArr.getAddition());
        }
        if (instruction instanceof PushArr) {
            PushArr pushArr = (PushArr) instruction;
            return values(pushArr.getTarget(), pushArr.getAddition());
        }
        if (instruction instanceof CallFunc) {
            return values(((CallFunc) instruction).getArgs());
        }
        if (instruction instanceof CallAsyncFunc) {
            return values(((CallAsyncFunc) instruction).getArgs());
        }
        if (instruction instanceof JumpIfFalse) {
            return values(((JumpIfFalse) instruction).getCond());
        }
        if (instruction instanceof JumpIfTrue) {
            return values(((JumpIfTrue) instruction).getCond());
        }
        if (instruction instanceof Ret) {
            return values(((Ret) instruction).getValue());
        }
        if (instruction instanceof Throw) {
            return values(((Throw) instruction).value);
        }
        return Collections.emptyList();
    }

    private static Set<SsaValue> edgeLiveIn(Edge edge, Set<SsaValue> successorLiveIn) {
        Set<SsaValue> out = newSet(successorLiveIn);
        for (Phi phi : edge.successor.getPhiValues()) {
            out.remove(phi.getResult());
            for (Phi.Case aCase : phi.getCases()) {
                if (aCase.from == edge.predecessor) {
                    out.add(aCase.value);
                    break;
                }
            }
        }
        return out;
    }

    private static List<SsaValue> values(SsaValue... values) {
        List<SsaValue> list = new ArrayList<>(values.length);
        Collections.addAll(list, values);
        return list;
    }

    private static Set<SsaValue> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
    }

    private static Set<SsaValue> newSet(Collection<SsaValue> values) {
        Set<SsaValue> set = newSet();
        set.addAll(values);
        return set;
    }

    public static class Result {
        private final Map<Block, Set<SsaValue>> liveIn;
        private final Map<Block, Set<SsaValue>> liveOut;
        private final Map<Instruction, Set<SsaValue>> liveBefore;
        private final Map<Instruction, Set<SsaValue>> liveAfter;

        private Result(Map<Block, Set<SsaValue>> liveIn,
                       Map<Block, Set<SsaValue>> liveOut,
                       Map<Instruction, Set<SsaValue>> liveBefore,
                       Map<Instruction, Set<SsaValue>> liveAfter) {
            this.liveIn = freeze(liveIn);
            this.liveOut = freeze(liveOut);
            this.liveBefore = freeze(liveBefore);
            this.liveAfter = freeze(liveAfter);
        }

        public Set<SsaValue> liveIn(Block block) {
            return get(liveIn, block);
        }

        public Set<SsaValue> liveOut(Block block) {
            return get(liveOut, block);
        }

        public Set<SsaValue> liveBefore(Instruction instruction) {
            return get(liveBefore, instruction);
        }

        public Set<SsaValue> liveAfter(Instruction instruction) {
            return get(liveAfter, instruction);
        }

        private static <K> Map<K, Set<SsaValue>> freeze(Map<K, Set<SsaValue>> source) {
            Map<K, Set<SsaValue>> copy = new IdentityHashMap<>();
            for (Map.Entry<K, Set<SsaValue>> entry : source.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableSet(newSet(entry.getValue())));
            }
            return copy;
        }

        private static <K> Set<SsaValue> get(Map<K, Set<SsaValue>> map, K key) {
            Set<SsaValue> values = map.get(key);
            return values == null ? Collections.<SsaValue>emptySet() : values;
        }
    }
}
