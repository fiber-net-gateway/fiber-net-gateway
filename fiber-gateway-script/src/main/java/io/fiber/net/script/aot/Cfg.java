package io.fiber.net.script.aot;

import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;

import java.util.*;

public class Cfg {
    private final TreeMap<Integer, Block> blockTreeMap = new TreeMap<>();
    private Block entryBlock;

    public void addBlock(int pc) {
        Integer idx = pc;
        if (blockTreeMap.containsKey(idx)) {
            return;
        }
        blockTreeMap.put(idx, new Block(pc));
    }

    public Block mustFindBlock(int pc) {
        return Objects.requireNonNull(blockTreeMap.floorEntry(pc).getValue());
    }

    Block mustGetBlock(int pc) {
        return Objects.requireNonNull(blockTreeMap.get(pc));
    }

    public Collection<Block> getBlocks() {
        return blockTreeMap.values();
    }

    Block getEntryBlock() {
        return Objects.requireNonNull(entryBlock);
    }

    void setEntryBlock(Block entryBlock) {
        if (!blockTreeMap.containsValue(entryBlock)) {
            throw new IllegalStateException("[bug]entry block not in cfg");
        }
        this.entryBlock = entryBlock;
    }

    void removeBlock(Block block) {
        if (block == entryBlock) {
            throw new IllegalStateException("[bug]cannot remove entry block");
        }
        for (Edge edge : new ArrayList<>(block.successors)) {
            removeEdge(edge);
        }
        for (Edge edge : new ArrayList<>(block.predecessors)) {
            removeEdge(edge);
        }
        for (Phi phi : new ArrayList<>(block.getPhiValues())) {
            block.removePhi(phi);
        }
        for (Instruction instruction : block.getInstructions()) {
            instruction.dropOperands();
        }
        blockTreeMap.remove(block.startPc);
    }

    void removeDetachedBlock(Block block) {
        if (block == entryBlock) {
            throw new IllegalStateException("[bug]cannot remove entry block");
        }
        if (!block.successors.isEmpty()
                || !block.predecessors.isEmpty()
                || !block.getPhiValues().isEmpty()
                || !block.getInstructions().isEmpty()) {
            throw new IllegalStateException("[bug]block still attached");
        }
        blockTreeMap.remove(block.startPc);
    }

    void removeEdge(Edge edge) {
        if (!edge.predecessor.successors.remove(edge)) {
            return;
        }
        edge.successor.predecessors.remove(edge);
        for (Phi phi : edge.successor.getPhiValues()) {
            phi.removeCase(edge.predecessor);
        }
    }

    void addEdge(Edge.Type type, Block predecessor, Block successor) {
        addEdge0(type, predecessor, successor);
    }

    void replaceValue(SsaValue oldVal, SsaValue newVal) {
        oldVal.replaceAllUsesWith(newVal);
        for (Block block : blockTreeMap.values()) {
            block.replaceFrameValue(oldVal, newVal);
        }
    }

    private static void addEdge0(Edge.Type type, Block predecessor, Block successor) {
        for (Edge edge : predecessor.successors) {
            if (edge.successor == successor) {
                if ((edge.type == Edge.Type.THROW) != (type == Edge.Type.THROW)) {
                    throw new IllegalStateException("[bug]mixed throw and normal edge from " +
                            predecessor.startPc + " to " + successor.startPc);
                }
                if (edge.type == type) {
                    return;
                }
            }
        }
        new Edge(type, predecessor, successor);
    }


    public static class Builder {

        Cfg cfg;
        final Compiled compiled;


        public Builder(Compiled compiled) {
            this.compiled = compiled;
        }

        private void addBlock(int pc) {
            if (pc < compiled.getCodes().length) {
                cfg.addBlock(pc);
            }
        }

        private Block mustGetByPc(int pc) {
            return cfg.mustGetBlock(pc);
        }

        public Cfg build() {
            cfg = new Cfg();
            addBlock(0);
            cfg.setEntryBlock(mustGetByPc(0));
            int[] codes = compiled.getCodes();
            for (int i = 0; i < codes.length; i++) {
                int code = codes[i];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP:
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        addBlock(code >>> 8);
                        addBlock(i + 1);
                        break;
                    }
                    case Code.INTO_CATCH: {
                        addBlock(i);
                    }
                    case Code.THROW_EXP:
                    case Code.END_RETURN:
                        addBlock(i + 1);
                        break;
                    default:
                        break;
                }
                Instruction.Throw aThrow = getThrow(c);
                if (aThrow == Instruction.Throw.MAYBE) {
                    addBlock(i + 1);
                }
            }

            {
                Block last = null;
                for (Map.Entry<Integer, Block> entry : cfg.blockTreeMap.entrySet()) {
                    Block value = entry.getValue();
                    if (last != null) {
                        last.endPc = value.startPc;
                    }
                    last = value;
                }
                if (last != null) {
                    last.endPc = codes.length;
                }
            }

            for (Block current : cfg.blockTreeMap.values()) {
                int pc = current.endPc - 1;
                if (pc < current.startPc) {
                    throw new IllegalStateException("[bug]end Pc < startPc ???");
                }
                int code = codes[pc];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP: {
                        Block block = mustGetByPc(code >>> 8);
                        cfg.addEdge(Edge.Type.JUMP, current, block);
                        break;
                    }
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        Block block = mustGetByPc(code >>> 8);
                        cfg.addEdge(Edge.Type.JUMP, current, block);
                        addFallthrough(current);
                        break;
                    }
                    default:
                        Instruction.Throw aThrow = getThrow(c);
                        if (aThrow != Instruction.Throw.NOT) {
                            int cpc = Compiled.searchExpHandle(pc, compiled.getExpIns());
                            if (cpc >= 0) {
                                cfg.addEdge(Edge.Type.THROW, current, mustGetByPc(cpc));
                            }
                            if (aThrow == Instruction.Throw.MAYBE) {
                                addFallthrough(current);
                            }
                        } else if (c != Code.END_RETURN) {
                            addFallthrough(current);
                        }
                }
            }

            propagateStackSize();

            for (Block block : cfg.blockTreeMap.values()) {
                block.simulate(compiled, cfg);
            }
            resolveSsa();
            simplifyPhis();
            optimizeCfg();
            return cfg;
        }

        private void optimizeCfg() {
            boolean changed;
            do {
                changed = false;
                changed |= new SparseConditionalConstPropagation(cfg).optimize();
                changed |= new AlgebraicSimplification(cfg).optimize();
                changed |= new AlwaysThrowPruning(cfg).optimize();
                changed |= new LocalCse(cfg).optimize();
                changed |= new GlobalValueNumbering(cfg).optimize();
                changed |= new LoopInvariantCodeMotion(cfg).optimize();
                changed |= new ExceptionEdgePruning(cfg).optimize();
                changed |= new JumpOptimization(cfg).optimize();
                changed |= new BranchElimination(cfg).optimize();
                simplifyPhis();
                changed |= new DeadCodeElimination(cfg).optimize();
                changed |= new EmptyBlockPruning(cfg).optimize();
                changed |= new JumpOnlyBlockPruning(cfg).optimize();
                changed |= new LinearBlockMerging(cfg).optimize();
                simplifyPhis();
            } while (changed);
        }

        private void propagateStackSize() {
            for (Block block : cfg.blockTreeMap.values()) {
                block.prepareStackShape(compiled);
            }

            Queue<Block> queue = new ArrayDeque<>();
            Block entry = cfg.getEntryBlock();
            entry.mergeInputStackSize(0);
            queue.add(entry);
            while (!queue.isEmpty()) {
                Block block = queue.poll();
                for (Edge edge : block.successors) {
                    int nextStackSize = edge.type == Edge.Type.THROW ? 0 : block.outputStackSize;
                    if (edge.successor.mergeInputStackSize(nextStackSize)) {
                        queue.add(edge.successor);
                    }
                }
            }

            for (Block block : cfg.blockTreeMap.values()) {
                block.checkInputStackSize();
            }
        }

        private void addFallthrough(Block current) {
            if (current.endPc >= compiled.getCodes().length) {
                return;
            }
            cfg.addEdge(Edge.Type.FALLTHROUGH, current, mustGetByPc(current.endPc));
        }

        private void resolveSsa() {
            ArrayDeque<MaybePhi> queue = new ArrayDeque<>();
            Set<MaybePhi> queued = Collections.newSetFromMap(new IdentityHashMap<MaybePhi, Boolean>());
            Set<MaybePhi> resolved = Collections.newSetFromMap(new IdentityHashMap<MaybePhi, Boolean>());
            enqueueMaybePhis(queue, queued);
            while (!queue.isEmpty()) {
                MaybePhi maybePhi = queue.poll();
                if (!resolved.add(maybePhi)) {
                    continue;
                }
                resolveMaybePhi(maybePhi);
                enqueueMaybePhis(queue, queued);
            }
        }

        private void enqueueMaybePhis(ArrayDeque<MaybePhi> queue, Set<MaybePhi> queued) {
            for (Block block : cfg.blockTreeMap.values()) {
                List<MaybePhi> maybePhis = block.getMaybePhis();
                for (MaybePhi maybePhi : maybePhis) {
                    if (queued.add(maybePhi)) {
                        queue.add(maybePhi);
                    }
                }
            }
        }

        private void resolveMaybePhi(MaybePhi maybePhi) {
            Block block = maybePhi.getBelongTo();
            List<Edge> predecessors = block.predecessors;
            if (predecessors.isEmpty()) {
                throw new IllegalStateException("[bug]unresolved entry " + describeMaybePhi(maybePhi));
            }

            SsaValue same = null;
            SsaValue[] values = new SsaValue[predecessors.size()];
            boolean different = false;
            for (int i = 0; i < values.length; i++) {
                SsaValue value = incomingValue(predecessors.get(i), maybePhi);
                values[i] = value;
                if (same == null) {
                    same = value;
                } else if (same != value) {
                    different = true;
                }
            }

            if (!different) {
                replaceValue(maybePhi.getResult(), same);
                return;
            }

            Phi phi = block.newPhi();
            for (int i = 0; i < values.length; i++) {
                phi.addCase(predecessors.get(i).predecessor, values[i]);
            }
            block.addPhi(phi);
            replaceValue(maybePhi.getResult(), phi.getResult());
        }

        private SsaValue incomingValue(Edge edge, MaybePhi maybePhi) {
            if (!maybePhi.isStack()) {
                return edge.predecessor.getExitVar(maybePhi.getIdx());
            }
            if (edge.type == Edge.Type.THROW) {
                throw new IllegalStateException("[bug]throw edge cannot provide stack " + describeMaybePhi(maybePhi));
            }
            SsaValue[] stack = edge.predecessor.getExitStack();
            int idx = maybePhi.getIdx();
            if (idx >= stack.length) {
                throw new IllegalStateException("[bug]missing stack " + describeMaybePhi(maybePhi) +
                        " from " + edge.predecessor.startPc);
            }
            SsaValue value = stack[idx];
            if (value == null) {
                throw new IllegalStateException("[bug]empty stack " + describeMaybePhi(maybePhi) +
                        " from " + edge.predecessor.startPc);
            }
            return value;
        }

        private void simplifyPhis() {
            boolean changed;
            do {
                changed = false;
                for (Block block : cfg.blockTreeMap.values()) {
                    List<Phi> phiValues = block.getPhiValues();
                    for (Phi phi : phiValues) {
                        SsaValue replacement = findTrivialPhiReplacement(phi);
                        if (replacement == null) {
                            continue;
                        }
                        replaceValue(phi.getResult(), replacement);
                        block.removePhi(phi);
                        changed = true;
                        break;
                    }
                    if (changed) {
                        break;
                    }
                }
            } while (changed);
        }

        private SsaValue findTrivialPhiReplacement(Phi phi) {
            SsaValue same = null;
            for (Phi.Case aCase : phi.getCases()) {
                SsaValue value = aCase.value;
                if (value == phi.getResult()) {
                    continue;
                }
                if (same == null) {
                    same = value;
                } else if (same != value) {
                    return null;
                }
            }
            return same;
        }

        private void replaceValue(SsaValue oldVal, SsaValue newVal) {
            oldVal.replaceAllUsesWith(newVal);
            for (Block block : cfg.blockTreeMap.values()) {
                block.replaceFrameValue(oldVal, newVal);
            }
        }

        private static String describeMaybePhi(MaybePhi maybePhi) {
            return (maybePhi.isStack() ? "stack" : "local") + '#' + maybePhi.getIdx() +
                    " at block " + maybePhi.getBelongTo().startPc;
        }

        public static Instruction.Throw getThrow(int c) {
            switch (c) {
                case Code.NOOP:
                case Code.LOAD_CONST:
                case Code.LOAD_ROOT:
                case Code.DUMP:
                case Code.POP:
                case Code.LOAD_VAR:
                case Code.STORE_VAR:

                case Code.NEW_OBJECT:
                case Code.NEW_ARRAY:
                case Code.EXP_OBJECT:
                case Code.EXP_ARRAY:
                case Code.PUSH_ARRAY:
                    return Instruction.Throw.NOT;

                //
                case Code.IDX_GET:
                    return Instruction.Throw.NOT;
                case Code.IDX_SET:
                case Code.IDX_SET_1:
                    return Instruction.Throw.MAYBE;
                case Code.PROP_GET:
                    return Instruction.Throw.NOT;
                case Code.PROP_SET:
                case Code.PROP_SET_1:
                    return Instruction.Throw.MAYBE;
                // hack 必需保持递增
                case Code.BOP_PLUS:
                case Code.BOP_MINUS:
                case Code.BOP_MULTIPLY:
                case Code.BOP_DIVIDE:
                case Code.BOP_MOD:
                    return Instruction.Throw.MAYBE;

                case Code.BOP_MATCH:
                case Code.BOP_LT:
                case Code.BOP_LTE:
                case Code.BOP_GT:
                case Code.BOP_GTE:
                case Code.BOP_EQ:
                case Code.BOP_SEQ:
                case Code.BOP_NE:
                case Code.BOP_SNE:
                case Code.BOP_IN:
                    return Instruction.Throw.NOT;
                // hack 必需保持递增
                case Code.UNARY_PLUS:
                case Code.UNARY_MINUS:
                    return Instruction.Throw.MAYBE;

                case Code.UNARY_NEG:
                case Code.UNARY_TYPEOF:
                    return Instruction.Throw.NOT;
                case Code.CALL_FUNC:
                case Code.CALL_FUNC_SPREAD:
                case Code.CALL_ASYNC_FUNC:
                case Code.CALL_ASYNC_FUNC_SPREAD:
                case Code.CALL_CONST:
                case Code.CALL_ASYNC_CONST:
                    return Instruction.Throw.MAYBE;
                //
                case Code.JUMP:
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:

                case Code.ITERATE_INTO:
                case Code.ITERATE_NEXT:
                case Code.ITERATE_KEY:
                case Code.ITERATE_VALUE:
                case Code.INTO_CATCH:
                    return Instruction.Throw.NOT;

                case Code.THROW_EXP:
                    return Instruction.Throw.ALWAYS;
                case Code.END_RETURN:
                    return Instruction.Throw.NOT;
                default:
                    throw new IllegalStateException("[bug]unknown code");
            }
        }
    }
}
