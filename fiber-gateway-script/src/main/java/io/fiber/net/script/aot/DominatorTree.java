package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DominatorTree {
    private final Cfg cfg;
    private final BitSet reachable;
    private final BitSet[] dominators;
    private final BasicBlock[] immediateDominators;
    private final List<BasicBlock>[] children;
    private final List<BasicBlock>[] dominanceFrontiers;

    public DominatorTree(Cfg cfg) {
        this.cfg = cfg;
        this.reachable = computeReachable(cfg);
        this.dominators = computeDominators(cfg, reachable);
        this.immediateDominators = computeImmediateDominators(cfg, reachable, dominators);
        this.children = computeChildren(cfg, immediateDominators);
        this.dominanceFrontiers = computeDominanceFrontiers(cfg, reachable, immediateDominators);
    }

    public Cfg cfg() {
        return cfg;
    }

    public boolean isReachable(BasicBlock block) {
        return block != null && reachable.get(block.id());
    }

    public boolean dominates(BasicBlock dominator, BasicBlock block) {
        if (dominator == null || block == null) {
            return false;
        }
        if (block.id() < 0 || block.id() >= dominators.length) {
            return false;
        }
        return dominators[block.id()].get(dominator.id());
    }

    public boolean strictlyDominates(BasicBlock dominator, BasicBlock block) {
        return dominator != block && dominates(dominator, block);
    }

    public BasicBlock immediateDominator(BasicBlock block) {
        if (block == null || block.id() < 0 || block.id() >= immediateDominators.length) {
            return null;
        }
        return immediateDominators[block.id()];
    }

    public List<BasicBlock> children(BasicBlock block) {
        if (block == null || block.id() < 0 || block.id() >= children.length) {
            return Collections.emptyList();
        }
        return children[block.id()];
    }

    public List<BasicBlock> dominanceFrontier(BasicBlock block) {
        if (block == null || block.id() < 0 || block.id() >= dominanceFrontiers.length) {
            return Collections.emptyList();
        }
        return dominanceFrontiers[block.id()];
    }

    private static BitSet computeReachable(Cfg cfg) {
        List<BasicBlock> blocks = cfg.blocks();
        BitSet reachable = new BitSet(blocks.size());
        ArrayList<BasicBlock> stack = new ArrayList<>();
        stack.add(cfg.entryBlock());
        reachable.set(cfg.entryBlock().id());
        while (!stack.isEmpty()) {
            BasicBlock block = stack.remove(stack.size() - 1);
            for (Edge edge : block.successors()) {
                BasicBlock to = edge.to();
                if (!reachable.get(to.id())) {
                    reachable.set(to.id());
                    stack.add(to);
                }
            }
        }
        return reachable;
    }

    private static BitSet[] computeDominators(Cfg cfg, BitSet reachable) {
        List<BasicBlock> blocks = cfg.blocks();
        int size = blocks.size();
        BitSet allReachable = (BitSet) reachable.clone();
        BitSet[] dominators = new BitSet[size];
        for (BasicBlock block : blocks) {
            BitSet set = new BitSet(size);
            if (block == cfg.entryBlock() || !reachable.get(block.id())) {
                set.set(block.id());
            } else {
                set.or(allReachable);
            }
            dominators[block.id()] = set;
        }

        boolean changed;
        do {
            changed = false;
            for (BasicBlock block : blocks) {
                if (block == cfg.entryBlock() || !reachable.get(block.id())) {
                    continue;
                }
                BitSet next = null;
                for (Edge edge : block.predecessors()) {
                    BasicBlock pred = edge.from();
                    if (!reachable.get(pred.id())) {
                        continue;
                    }
                    if (next == null) {
                        next = (BitSet) dominators[pred.id()].clone();
                    } else {
                        next.and(dominators[pred.id()]);
                    }
                }
                if (next == null) {
                    next = new BitSet(size);
                }
                next.set(block.id());
                if (!next.equals(dominators[block.id()])) {
                    dominators[block.id()] = next;
                    changed = true;
                }
            }
        } while (changed);
        return dominators;
    }

    private static BasicBlock[] computeImmediateDominators(Cfg cfg, BitSet reachable, BitSet[] dominators) {
        List<BasicBlock> blocks = cfg.blocks();
        BasicBlock[] idoms = new BasicBlock[blocks.size()];
        for (BasicBlock block : blocks) {
            if (block == cfg.entryBlock() || !reachable.get(block.id())) {
                continue;
            }
            BitSet strictDominators = (BitSet) dominators[block.id()].clone();
            strictDominators.clear(block.id());
            for (int id = strictDominators.nextSetBit(0); id >= 0; id = strictDominators.nextSetBit(id + 1)) {
                boolean immediate = true;
                for (int other = strictDominators.nextSetBit(0);
                     other >= 0;
                     other = strictDominators.nextSetBit(other + 1)) {
                    if (other != id && !dominators[id].get(other)) {
                        immediate = false;
                        break;
                    }
                }
                if (immediate) {
                    idoms[block.id()] = blocks.get(id);
                    break;
                }
            }
        }
        return idoms;
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] computeChildren(Cfg cfg, BasicBlock[] idoms) {
        int size = cfg.blocks().size();
        ArrayList<BasicBlock>[] children = new ArrayList[size];
        for (int i = 0; i < size; i++) {
            children[i] = new ArrayList<>();
        }
        for (BasicBlock block : cfg.blocks()) {
            BasicBlock idom = idoms[block.id()];
            if (idom != null) {
                children[idom.id()].add(block);
            }
        }
        return freeze(children);
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] computeDominanceFrontiers(Cfg cfg, BitSet reachable, BasicBlock[] idoms) {
        int size = cfg.blocks().size();
        LinkedHashSet<BasicBlock>[] frontiers = new LinkedHashSet[size];
        for (int i = 0; i < size; i++) {
            frontiers[i] = new LinkedHashSet<>();
        }
        for (BasicBlock block : cfg.blocks()) {
            if (!reachable.get(block.id()) || block.predecessors().size() < 2) {
                continue;
            }
            BasicBlock stop = idoms[block.id()];
            for (Edge edge : block.predecessors()) {
                BasicBlock runner = edge.from();
                if (!reachable.get(runner.id())) {
                    continue;
                }
                while (runner != null && runner != stop) {
                    frontiers[runner.id()].add(block);
                    runner = idoms[runner.id()];
                }
            }
        }
        return freeze(frontiers);
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] freeze(List<BasicBlock>[] input) {
        List<BasicBlock>[] output = new List[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = Collections.unmodifiableList(input[i]);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] freeze(Set<BasicBlock>[] input) {
        List<BasicBlock>[] output = new List[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = Collections.unmodifiableList(new ArrayList<>(input[i]));
        }
        return output;
    }
}
