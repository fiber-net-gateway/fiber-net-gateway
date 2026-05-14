package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ScalarReplacement {

    private final Cfg cfg;
    private final OptimizationContext context;
    private boolean changed;

    public ScalarReplacement(Cfg cfg) {
        this(cfg, new OptimizationContext(cfg));
    }

    ScalarReplacement(Cfg cfg, OptimizationContext context) {
        this.cfg = cfg;
        this.context = context;
    }

    public boolean optimize() {
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (instruction instanceof NewObj) {
                    optimizeCandidate((NewObj) instruction);
                } else if (instruction instanceof NewArr) {
                    optimizeArrayCandidate((NewArr) instruction);
                }
            }
        }
        return changed;
    }

    private void optimizeCandidate(NewObj allocation) {
        Candidate candidate = collectCandidate(allocation);
        if (candidate == null || touchesLoop(candidate.useBlocks)) {
            return;
        }

        Analysis analysis = analyze(candidate);
        if (analysis.failed || analysis.actions.isEmpty()) {
            return;
        }
        for (RewriteAction action : analysis.actions) {
            action.apply(cfg);
        }
        changed = true;
    }

    private void optimizeArrayCandidate(NewArr allocation) {
        ArrayCandidate candidate = collectArrayCandidate(allocation);
        if (candidate == null || touchesLoop(candidate.useBlocks)) {
            return;
        }

        ArrayAnalysis analysis = analyzeArray(candidate);
        if (analysis.failed || analysis.actions.isEmpty()) {
            return;
        }
        for (RewriteAction action : analysis.actions) {
            action.apply(cfg);
        }
        changed = true;
    }

    private static Integer constArrayIndex(SsaValue value) {
        ValueNode node = ConstantValues.valueOf(value);
        return node != null && node.isIntegralNumber() ? node.intValue() : null;
    }

    private Candidate collectCandidate(NewObj allocation) {
        Candidate candidate = new Candidate(allocation);
        boolean updated;
        do {
            updated = false;
            List<SsaValue> aliases = new ArrayList<>(candidate.aliases);
            for (SsaValue alias : aliases) {
                for (Instruction used : alias.getUsed()) {
                    if (used instanceof PropGet) {
                        PropGet propGet = (PropGet) used;
                        if (propGet.getOwner() != alias) {
                            return null;
                        }
                        candidate.reads.add(propGet);
                        candidate.useBlocks.add(propGet.getBelongTo());
                    } else if (used instanceof PropSet) {
                        PropSet propSet = (PropSet) used;
                        if (propSet.getOwner() != alias || propSet.getAlien() == alias) {
                            return null;
                        }
                        candidate.writes.add(propSet);
                        candidate.useBlocks.add(propSet.getBelongTo());
                    } else if (used instanceof PropSet1) {
                        PropSet1 propSet = (PropSet1) used;
                        if (propSet.getOwner() != alias || propSet.getAlien() == alias) {
                            return null;
                        }
                        candidate.writes.add(propSet);
                        candidate.useBlocks.add(propSet.getBelongTo());
                        updated |= candidate.aliases.add(propSet.getResult());
                    } else if (used instanceof Phi) {
                        Phi phi = (Phi) used;
                        if (!allPhiInputsAreAliases(phi, candidate.aliases)) {
                            return null;
                        }
                        updated |= candidate.aliases.add(phi.getResult());
                        candidate.useBlocks.add(phi.getBelongTo());
                    } else {
                        return null;
                    }
                }
            }
        } while (updated);
        return candidate;
    }

    private ArrayCandidate collectArrayCandidate(NewArr allocation) {
        ArrayCandidate candidate = new ArrayCandidate(allocation);
        boolean updated;
        do {
            updated = false;
            List<SsaValue> aliases = new ArrayList<>(candidate.aliases);
            for (SsaValue alias : aliases) {
                for (Instruction used : alias.getUsed()) {
                    if (used instanceof PushArr) {
                        PushArr pushArr = (PushArr) used;
                        if (pushArr.getTarget() != alias || candidate.aliases.contains(pushArr.getAddition())) {
                            return null;
                        }
                        candidate.writes.add(pushArr);
                        candidate.useBlocks.add(pushArr.getBelongTo());
                        updated |= candidate.aliases.add(pushArr.getResult());
                    } else if (used instanceof IndexGet) {
                        IndexGet indexGet = (IndexGet) used;
                        if (indexGet.getOwner() != alias || constArrayIndex(indexGet.getKey()) == null) {
                            return null;
                        }
                        candidate.reads.add(indexGet);
                        candidate.useBlocks.add(indexGet.getBelongTo());
                    } else if (used instanceof IndexSet) {
                        IndexSet indexSet = (IndexSet) used;
                        if (indexSet.getOwner() != alias
                                || constArrayIndex(indexSet.getKey()) == null
                                || candidate.aliases.contains(indexSet.getAlien())) {
                            return null;
                        }
                        candidate.writes.add(indexSet);
                        candidate.useBlocks.add(indexSet.getBelongTo());
                    } else if (used instanceof IndexSet1) {
                        IndexSet1 indexSet = (IndexSet1) used;
                        if (indexSet.getOwner() != alias
                                || constArrayIndex(indexSet.getKey()) == null
                                || candidate.aliases.contains(indexSet.getAlien())) {
                            return null;
                        }
                        candidate.writes.add(indexSet);
                        candidate.useBlocks.add(indexSet.getBelongTo());
                        updated |= candidate.aliases.add(indexSet.getResult());
                    } else if (used instanceof Phi) {
                        Phi phi = (Phi) used;
                        if (!allPhiInputsAreAliases(phi, candidate.aliases)) {
                            return null;
                        }
                        updated |= candidate.aliases.add(phi.getResult());
                        candidate.useBlocks.add(phi.getBelongTo());
                    } else {
                        return null;
                    }
                }
            }
        } while (updated);
        return candidate;
    }

    private static boolean allPhiInputsAreAliases(Phi phi, Set<SsaValue> aliases) {
        for (Phi.Case aCase : phi.getCases()) {
            if (!aliases.contains(aCase.value)) {
                return false;
            }
        }
        return true;
    }

    private boolean touchesLoop(Set<Block> useBlocks) {
        Dominators dominators = context.dominators();
        for (Block block : dominators.reversePostOrder) {
            for (Edge edge : block.getSuccessors()) {
                if (edge.type == Edge.Type.THROW || !dominators.dominates(edge.successor, block)) {
                    continue;
                }
                Set<Block> loopBlocks = naturalLoop(edge.successor, block);
                for (Block useBlock : useBlocks) {
                    if (loopBlocks.contains(useBlock)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<Block> naturalLoop(Block header, Block latch) {
        Set<Block> loopBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        List<Block> queue = new ArrayList<>();
        loopBlocks.add(header);
        loopBlocks.add(latch);
        queue.add(latch);
        for (int i = 0; i < queue.size(); i++) {
            Block block = queue.get(i);
            for (Edge edge : block.getPredecessors()) {
                if (edge.type == Edge.Type.THROW) {
                    continue;
                }
                Block predecessor = edge.predecessor;
                if (loopBlocks.add(predecessor) && predecessor != header) {
                    queue.add(predecessor);
                }
            }
        }
        return loopBlocks;
    }

    private Analysis analyze(Candidate candidate) {
        Analysis analysis = new Analysis(candidate);
        Map<Block, State> outStates = new IdentityHashMap<>();
        Map<Block, State> inStates = new IdentityHashMap<>();
        List<Block> rpo = context.dominators().reversePostOrder;
        for (Block block : rpo) {
            outStates.put(block, State.EMPTY);
        }

        boolean updated;
        int iterations = 0;
        do {
            updated = false;
            for (Block block : rpo) {
                State in = mergePredecessors(block, candidate, outStates, analysis);
                State oldIn = inStates.put(block, in);
                State out = transfer(block, in, candidate, analysis);
                if (analysis.failed) {
                    return analysis;
                }
                if (!in.equals(oldIn) || !out.equals(outStates.get(block))) {
                    outStates.put(block, out);
                    updated = true;
                }
            }
        } while (updated && ++iterations <= rpo.size());

        if (updated) {
            analysis.failed = true;
            return analysis;
        }
        analysis.finish(candidate);
        return analysis;
    }

    private ArrayAnalysis analyzeArray(ArrayCandidate candidate) {
        ArrayAnalysis analysis = new ArrayAnalysis(candidate);
        Map<Block, ArrayState> outStates = new IdentityHashMap<>();
        Map<Block, ArrayState> inStates = new IdentityHashMap<>();
        List<Block> rpo = context.dominators().reversePostOrder;
        for (Block block : rpo) {
            outStates.put(block, ArrayState.EMPTY);
        }

        boolean updated;
        int iterations = 0;
        do {
            updated = false;
            for (Block block : rpo) {
                ArrayState in = mergeArrayPredecessors(block, outStates, analysis);
                if (analysis.failed) {
                    return analysis;
                }
                ArrayState oldIn = inStates.put(block, in);
                ArrayState out = transferArray(block, in, candidate, analysis);
                if (analysis.failed) {
                    return analysis;
                }
                if (!in.equals(oldIn) || !out.equals(outStates.get(block))) {
                    outStates.put(block, out);
                    updated = true;
                }
            }
        } while (updated && ++iterations <= rpo.size());

        if (updated) {
            analysis.failed = true;
            return analysis;
        }
        analysis.finish(candidate);
        return analysis;
    }

    private ArrayState mergeArrayPredecessors(Block block,
                                              Map<Block, ArrayState> outStates,
                                              ArrayAnalysis analysis) {
        if (block == cfg.getEntryBlock()) {
            return ArrayState.EMPTY;
        }

        List<Edge> predecessors = block.getPredecessors();
        List<Block> predBlocks = new ArrayList<>();
        List<ArrayState> predStates = new ArrayList<>();
        for (Edge edge : predecessors) {
            if (edge.type == Edge.Type.THROW) {
                continue;
            }
            ArrayState predecessorState = outStates.get(edge.predecessor);
            if (predecessorState == null) {
                predecessorState = ArrayState.EMPTY;
            }
            predBlocks.add(edge.predecessor);
            predStates.add(predecessorState);
        }
        if (predStates.isEmpty()) {
            return ArrayState.EMPTY;
        }

        ArrayState first = predStates.get(0);
        for (int i = 1; i < predStates.size(); i++) {
            if (predStates.get(i).length != first.length) {
                analysis.failed = true;
                return first;
            }
        }

        Map<Integer, SsaValue> indexes = new HashMap<>();
        for (Map.Entry<Integer, SsaValue> entry : first.indexes.entrySet()) {
            Integer index = entry.getKey();
            SsaValue same = entry.getValue();
            boolean different = false;
            SsaValue[] values = new SsaValue[predStates.size()];
            for (int i = 0; i < predStates.size(); i++) {
                SsaValue value = predStates.get(i).indexes.get(index);
                if (value == null) {
                    values = null;
                    break;
                }
                values[i] = value;
                if (same != value) {
                    different = true;
                }
            }
            if (values == null) {
                continue;
            }
            indexes.put(index, different ? analysis.phi(block, new Slot(index), predBlocks, values) : same);
        }
        return indexes.isEmpty() && first.length == 0 ? ArrayState.EMPTY : new ArrayState(first.length, indexes);
    }

    private ArrayState transferArray(Block block,
                                     ArrayState input,
                                     ArrayCandidate candidate,
                                     ArrayAnalysis analysis) {
        ArrayState state = input;
        for (Instruction instruction : block.getInstructions()) {
            if (instruction == candidate.allocation) {
                continue;
            }
            if (instruction instanceof PushArr) {
                PushArr pushArr = (PushArr) instruction;
                if (!candidate.aliases.contains(pushArr.getTarget())) {
                    continue;
                }
                state = state.push(pushArr.getAddition());
                analysis.remove(pushArr, candidate.allocation.getResult());
            } else if (instruction instanceof IndexSet) {
                IndexSet indexSet = (IndexSet) instruction;
                if (!candidate.aliases.contains(indexSet.getOwner())) {
                    continue;
                }
                Integer idx = constArrayIndex(indexSet.getKey());
                if (idx == null || idx < 0 || idx >= state.length) {
                    analysis.failed = true;
                    return state;
                }
                state = state.with(idx, indexSet.getAlien());
                analysis.remove(indexSet, indexSet.getAlien());
            } else if (instruction instanceof IndexSet1) {
                IndexSet1 indexSet = (IndexSet1) instruction;
                if (!candidate.aliases.contains(indexSet.getOwner())) {
                    continue;
                }
                Integer idx = constArrayIndex(indexSet.getKey());
                if (idx == null || idx < 0 || idx >= state.length) {
                    analysis.failed = true;
                    return state;
                }
                state = state.with(idx, indexSet.getAlien());
                analysis.remove(indexSet, candidate.allocation.getResult());
            } else if (instruction instanceof IndexGet) {
                IndexGet indexGet = (IndexGet) instruction;
                if (!candidate.aliases.contains(indexGet.getOwner())) {
                    continue;
                }
                Integer idx = constArrayIndex(indexGet.getKey());
                SsaValue replacement = idx == null ? null : state.indexes.get(idx);
                if (replacement == null) {
                    analysis.failed = true;
                    return state;
                }
                analysis.remove(indexGet, replacement);
            }
        }
        return state;
    }

    private State mergePredecessors(Block block,
                                    Candidate candidate,
                                    Map<Block, State> outStates,
                                    Analysis analysis) {
        if (block == cfg.getEntryBlock()) {
            return State.EMPTY;
        }

        List<Edge> predecessors = block.getPredecessors();
        List<Block> predBlocks = new ArrayList<>();
        List<State> predStates = new ArrayList<>();
        for (Edge edge : predecessors) {
            if (edge.type == Edge.Type.THROW) {
                continue;
            }
            State predecessorState = outStates.get(edge.predecessor);
            if (predecessorState == null) {
                predecessorState = State.EMPTY;
            }
            predBlocks.add(edge.predecessor);
            predStates.add(predecessorState);
        }
        if (predStates.isEmpty()) {
            return State.EMPTY;
        }
        State first = predStates.get(0);
        Map<Slot, SsaValue> slots = new HashMap<>();
        for (Map.Entry<Slot, SsaValue> entry : first.slots.entrySet()) {
            Slot slot = entry.getKey();
            SsaValue same = entry.getValue();
            boolean different = false;
            SsaValue[] values = new SsaValue[predStates.size()];
            for (int i = 0; i < predStates.size(); i++) {
                SsaValue value = predStates.get(i).slots.get(slot);
                if (value == null) {
                    values = null;
                    break;
                }
                values[i] = value;
                if (same != value) {
                    different = true;
                }
            }
            if (values == null) {
                continue;
            }
            slots.put(slot, different ? analysis.phi(block, slot, predBlocks, values) : same);
        }
        return slots.isEmpty() ? State.EMPTY : new State(slots);
    }

    private State transfer(Block block, State input, Candidate candidate, Analysis analysis) {
        State state = input;
        for (Instruction instruction : block.getInstructions()) {
            if (instruction == candidate.allocation) {
                continue;
            }
            if (instruction instanceof PropSet) {
                PropSet propSet = (PropSet) instruction;
                if (!candidate.aliases.contains(propSet.getOwner())) {
                    continue;
                }
                state = state.with(new Slot(propSet.getKey()), propSet.getAlien());
                analysis.remove(propSet, propSet.getAlien());
            } else if (instruction instanceof PropSet1) {
                PropSet1 propSet = (PropSet1) instruction;
                if (!candidate.aliases.contains(propSet.getOwner())) {
                    continue;
                }
                state = state.with(new Slot(propSet.getKey()), propSet.getAlien());
                analysis.remove(propSet, candidate.allocation.getResult());
            } else if (instruction instanceof PropGet) {
                PropGet propGet = (PropGet) instruction;
                if (!candidate.aliases.contains(propGet.getOwner())) {
                    continue;
                }
                SsaValue replacement = state.slots.get(new Slot(propGet.getKey()));
                if (replacement == null) {
                    analysis.failed = true;
                    return state;
                }
                analysis.remove(propGet, replacement);
            }
        }
        return state;
    }

    private static final class Candidate {
        final NewObj allocation;
        final Set<SsaValue> aliases = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        final Set<Block> useBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        final Set<Instruction> reads = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());
        final Set<Instruction> writes = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());

        Candidate(NewObj allocation) {
            this.allocation = allocation;
            aliases.add(allocation.getResult());
            useBlocks.add(allocation.getBelongTo());
        }
    }

    private static final class ArrayCandidate {
        final NewArr allocation;
        final Set<SsaValue> aliases = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        final Set<Block> useBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        final Set<Instruction> reads = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());
        final Set<Instruction> writes = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());

        ArrayCandidate(NewArr allocation) {
            this.allocation = allocation;
            aliases.add(allocation.getResult());
            useBlocks.add(allocation.getBelongTo());
        }
    }

    private static final class Analysis {
        final Candidate candidate;
        final List<RewriteAction> actions = new ArrayList<>();
        final Map<PhiKey, PhiPlan> phis = new HashMap<>();
        final Set<Instruction> removed = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());
        boolean failed;

        Analysis(Candidate candidate) {
            this.candidate = candidate;
        }

        SsaValue phi(Block block, Slot slot, List<Block> predecessors, SsaValue[] values) {
            PhiKey key = new PhiKey(block, slot, predecessors, values);
            PhiPlan plan = phis.get(key);
            if (plan == null) {
                plan = new PhiPlan(block.newPhi(), predecessors, values);
                phis.put(key, plan);
            }
            return plan.phi.getResult();
        }

        void remove(Expr expr, SsaValue replacement) {
            if (removed.add(expr)) {
                actions.add(new RewriteAction(expr, replacement));
            }
        }

        void finish(Candidate candidate) {
            for (PhiPlan plan : phis.values()) {
                Phi phi = plan.phi;
                if (!phi.getCases().isEmpty()) {
                    continue;
                }
                for (int i = 0; i < plan.predecessors.size(); i++) {
                    phi.addCase(plan.predecessors.get(i), plan.values[i]);
                }
                phi.getBelongTo().addPhi(phi);
            }
            if (!candidate.reads.isEmpty() && !removed.containsAll(candidate.reads)) {
                failed = true;
            }
            if (!candidate.writes.isEmpty() && !removed.containsAll(candidate.writes)) {
                failed = true;
            }
        }
    }

    private static final class ArrayAnalysis {
        final ArrayCandidate candidate;
        final List<RewriteAction> actions = new ArrayList<>();
        final Map<PhiKey, PhiPlan> phis = new HashMap<>();
        final Set<Instruction> removed = Collections.newSetFromMap(new IdentityHashMap<Instruction, Boolean>());
        boolean failed;

        ArrayAnalysis(ArrayCandidate candidate) {
            this.candidate = candidate;
        }

        SsaValue phi(Block block, Slot slot, List<Block> predecessors, SsaValue[] values) {
            PhiKey key = new PhiKey(block, slot, predecessors, values);
            PhiPlan plan = phis.get(key);
            if (plan == null) {
                plan = new PhiPlan(block.newPhi(), predecessors, values);
                phis.put(key, plan);
            }
            return plan.phi.getResult();
        }

        void remove(Expr expr, SsaValue replacement) {
            if (removed.add(expr)) {
                actions.add(new RewriteAction(expr, replacement));
            }
        }

        void removeInstruction(Instruction instruction) {
            if (removed.add(instruction)) {
                actions.add(new RemoveInstructionAction(instruction));
            }
        }

        void finish(ArrayCandidate candidate) {
            for (PhiPlan plan : phis.values()) {
                Phi phi = plan.phi;
                if (!phi.getCases().isEmpty()) {
                    continue;
                }
                for (int i = 0; i < plan.predecessors.size(); i++) {
                    phi.addCase(plan.predecessors.get(i), plan.values[i]);
                }
                phi.getBelongTo().addPhi(phi);
            }
            if (!candidate.reads.isEmpty() && !removed.containsAll(candidate.reads)) {
                failed = true;
            }
            if (!candidate.writes.isEmpty() && !removed.containsAll(candidate.writes)) {
                failed = true;
            }
        }
    }

    private static class RewriteAction {
        final Expr expr;
        final SsaValue replacement;

        RewriteAction(Expr expr, SsaValue replacement) {
            this.expr = expr;
            this.replacement = replacement;
        }

        void apply(Cfg cfg) {
            Block block = expr.getBelongTo();
            cfg.replaceValue(expr.getResult(), replacement);
            expr.dropOperands();
            block.removeInstruction(expr);
        }
    }

    private static final class RemoveInstructionAction extends RewriteAction {
        final Instruction instruction;

        RemoveInstructionAction(Instruction instruction) {
            super(null, null);
            this.instruction = instruction;
        }

        @Override
        void apply(Cfg cfg) {
            instruction.dropOperands();
            instruction.getBelongTo().removeInstruction(instruction);
        }
    }

    private static final class State {
        static final State EMPTY = new State(Collections.<Slot, SsaValue>emptyMap());

        final Map<Slot, SsaValue> slots;

        State(Map<Slot, SsaValue> slots) {
            this.slots = slots;
        }

        State with(Slot slot, SsaValue value) {
            Map<Slot, SsaValue> next = new HashMap<>(slots);
            next.put(slot, value);
            return new State(next);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof State)) {
                return false;
            }
            State state = (State) o;
            return slots.equals(state.slots);
        }

        @Override
        public int hashCode() {
            return slots.hashCode();
        }
    }

    private static final class ArrayState {
        static final ArrayState EMPTY = new ArrayState(0, Collections.<Integer, SsaValue>emptyMap());

        final int length;
        final Map<Integer, SsaValue> indexes;

        ArrayState(int length, Map<Integer, SsaValue> indexes) {
            this.length = length;
            this.indexes = indexes;
        }

        ArrayState push(SsaValue value) {
            Map<Integer, SsaValue> next = new HashMap<>(indexes);
            next.put(length, value);
            return new ArrayState(length + 1, next);
        }

        ArrayState with(int index, SsaValue value) {
            Map<Integer, SsaValue> next = new HashMap<>(indexes);
            next.put(index, value);
            return new ArrayState(length, next);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ArrayState)) {
                return false;
            }
            ArrayState that = (ArrayState) o;
            return length == that.length && indexes.equals(that.indexes);
        }

        @Override
        public int hashCode() {
            return 31 * length + indexes.hashCode();
        }
    }

    private static final class Slot {
        final Object key;

        Slot(String key) {
            this.key = key;
        }

        Slot(int key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Slot)) {
                return false;
            }
            Slot slot = (Slot) o;
            return Objects.equals(key, slot.key);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key);
        }
    }

    private static final class PhiKey {
        final Block block;
        final Slot slot;
        final List<Block> predecessors;
        final SsaValue[] values;

        PhiKey(Block block, Slot slot, List<Block> predecessors, SsaValue[] values) {
            this.block = block;
            this.slot = slot;
            this.predecessors = new ArrayList<>(predecessors);
            this.values = values.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PhiKey)) {
                return false;
            }
            PhiKey phiKey = (PhiKey) o;
            if (block != phiKey.block
                    || !Objects.equals(slot, phiKey.slot)
                    || predecessors.size() != phiKey.predecessors.size()) {
                return false;
            }
            for (int i = 0; i < predecessors.size(); i++) {
                if (predecessors.get(i) != phiKey.predecessors.get(i)
                        || values[i] != phiKey.values[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(block);
            result = 31 * result + slot.hashCode();
            for (int i = 0; i < predecessors.size(); i++) {
                result = 31 * result + System.identityHashCode(predecessors.get(i));
                result = 31 * result + System.identityHashCode(values[i]);
            }
            return result;
        }
    }

    private static final class PhiPlan {
        final Phi phi;
        final List<Block> predecessors;
        final SsaValue[] values;

        PhiPlan(Phi phi, List<Block> predecessors, SsaValue[] values) {
            this.phi = phi;
            this.predecessors = new ArrayList<>(predecessors);
            this.values = values.clone();
        }
    }
}
