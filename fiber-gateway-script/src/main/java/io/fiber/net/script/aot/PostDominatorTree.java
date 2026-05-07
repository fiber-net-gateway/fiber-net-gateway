package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PostDominatorTree {
    private final Cfg cfg;
    private final int blockCount;
    private final int syntheticExitId;
    private final BasicBlock syntheticExit;
    private final List<BasicBlock> exitBlocks;
    private final BitSet reverseReachable;
    private final BitSet[] postDominators;
    private final BasicBlock[] immediatePostDominators;
    private final List<BasicBlock>[] children;
    private final List<BasicBlock>[] postDominanceFrontiers;

    public PostDominatorTree(Cfg cfg) {
        this.cfg = cfg;
        this.blockCount = cfg.blocks().size();
        this.syntheticExitId = blockCount;
        this.syntheticExit = new BasicBlock(syntheticExitId, -1, -1, new Bytecode[0]);
        this.exitBlocks = Collections.unmodifiableList(computeExitBlocks(cfg));
        int[][] successors = computeSuccessors(cfg, blockCount, syntheticExitId, exitBlocks);
        int[][] predecessors = computePredecessors(successors);
        this.reverseReachable = computeReverseReachable(predecessors, syntheticExitId);
        this.postDominators = computePostDominators(successors, reverseReachable, syntheticExitId);
        this.immediatePostDominators = computeImmediatePostDominators(cfg, blockCount, syntheticExit,
                reverseReachable, postDominators);
        this.children = computeChildren(cfg, blockCount, syntheticExitId, immediatePostDominators);
        this.postDominanceFrontiers = computePostDominanceFrontiers(cfg, immediatePostDominators);
    }

    public Cfg cfg() {
        return cfg;
    }

    public BasicBlock syntheticExit() {
        return syntheticExit;
    }

    public List<BasicBlock> exitBlocks() {
        return exitBlocks;
    }

    public boolean isReverseReachable(BasicBlock block) {
        int id = idOf(block);
        return id >= 0 && reverseReachable.get(id);
    }

    public boolean postDominates(BasicBlock postDominator, BasicBlock block) {
        int postDominatorId = idOf(postDominator);
        int blockId = idOf(block);
        if (postDominatorId < 0 || blockId < 0) {
            return false;
        }
        return postDominators[blockId].get(postDominatorId);
    }

    public boolean strictlyPostDominates(BasicBlock postDominator, BasicBlock block) {
        return postDominator != block && postDominates(postDominator, block);
    }

    public BasicBlock immediatePostDominator(BasicBlock block) {
        int id = idOf(block);
        if (id < 0 || id >= immediatePostDominators.length) {
            return null;
        }
        return immediatePostDominators[id];
    }

    public List<BasicBlock> children(BasicBlock block) {
        int id = idOf(block);
        if (id < 0 || id >= children.length) {
            return Collections.emptyList();
        }
        return children[id];
    }

    public List<BasicBlock> postDominanceFrontier(BasicBlock block) {
        int id = idOf(block);
        if (id < 0 || id >= postDominanceFrontiers.length) {
            return Collections.emptyList();
        }
        return postDominanceFrontiers[id];
    }

    private int idOf(BasicBlock block) {
        if (block == null) {
            return -1;
        }
        if (block == syntheticExit) {
            return syntheticExitId;
        }
        int id = block.id();
        return id >= 0 && id < blockCount ? id : -1;
    }

    private static List<BasicBlock> computeExitBlocks(Cfg cfg) {
        ArrayList<BasicBlock> exits = new ArrayList<>();
        for (BasicBlock block : cfg.blocks()) {
            if (block.successors().isEmpty()) {
                exits.add(block);
            }
        }
        return exits;
    }

    private static int[][] computeSuccessors(Cfg cfg, int blockCount, int syntheticExitId, List<BasicBlock> exits) {
        int[][] successors = new int[blockCount + 1][];
        for (BasicBlock block : cfg.blocks()) {
            if (block.successors().isEmpty()) {
                successors[block.id()] = new int[]{syntheticExitId};
                continue;
            }
            int[] ids = new int[block.successors().size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = block.successors().get(i).to().id();
            }
            successors[block.id()] = ids;
        }
        successors[syntheticExitId] = new int[0];
        if (exits.isEmpty()) {
            successors[syntheticExitId] = new int[]{syntheticExitId};
        }
        return successors;
    }

    @SuppressWarnings("unchecked")
    private static int[][] computePredecessors(int[][] successors) {
        ArrayList<Integer>[] predecessors = new ArrayList[successors.length];
        for (int i = 0; i < predecessors.length; i++) {
            predecessors[i] = new ArrayList<>();
        }
        for (int from = 0; from < successors.length; from++) {
            for (int to : successors[from]) {
                predecessors[to].add(from);
            }
        }
        int[][] output = new int[successors.length][];
        for (int i = 0; i < predecessors.length; i++) {
            output[i] = new int[predecessors[i].size()];
            for (int j = 0; j < output[i].length; j++) {
                output[i][j] = predecessors[i].get(j);
            }
        }
        return output;
    }

    private static BitSet computeReverseReachable(int[][] predecessors, int syntheticExitId) {
        BitSet reachable = new BitSet(predecessors.length);
        ArrayList<Integer> stack = new ArrayList<>();
        reachable.set(syntheticExitId);
        stack.add(syntheticExitId);
        while (!stack.isEmpty()) {
            int id = stack.remove(stack.size() - 1);
            for (int pred : predecessors[id]) {
                if (!reachable.get(pred)) {
                    reachable.set(pred);
                    stack.add(pred);
                }
            }
        }
        return reachable;
    }

    private static BitSet[] computePostDominators(int[][] successors, BitSet reverseReachable, int syntheticExitId) {
        int size = successors.length;
        BitSet allReachable = (BitSet) reverseReachable.clone();
        BitSet[] postDominators = new BitSet[size];
        for (int i = 0; i < size; i++) {
            BitSet set = new BitSet(size);
            if (i == syntheticExitId || !reverseReachable.get(i)) {
                set.set(i);
            } else {
                set.or(allReachable);
            }
            postDominators[i] = set;
        }

        boolean changed;
        do {
            changed = false;
            for (int id = 0; id < size; id++) {
                if (id == syntheticExitId || !reverseReachable.get(id)) {
                    continue;
                }
                BitSet next = null;
                for (int succ : successors[id]) {
                    if (!reverseReachable.get(succ)) {
                        continue;
                    }
                    if (next == null) {
                        next = (BitSet) postDominators[succ].clone();
                    } else {
                        next.and(postDominators[succ]);
                    }
                }
                if (next == null) {
                    next = new BitSet(size);
                }
                next.set(id);
                if (!next.equals(postDominators[id])) {
                    postDominators[id] = next;
                    changed = true;
                }
            }
        } while (changed);
        return postDominators;
    }

    private static BasicBlock[] computeImmediatePostDominators(Cfg cfg,
                                                               int blockCount,
                                                               BasicBlock syntheticExit,
                                                               BitSet reverseReachable,
                                                               BitSet[] postDominators) {
        BasicBlock[] ipdoms = new BasicBlock[blockCount + 1];
        for (int id = 0; id < blockCount; id++) {
            if (!reverseReachable.get(id)) {
                continue;
            }
            BitSet strictPostDominators = (BitSet) postDominators[id].clone();
            strictPostDominators.clear(id);
            for (int candidate = strictPostDominators.nextSetBit(0);
                 candidate >= 0;
                 candidate = strictPostDominators.nextSetBit(candidate + 1)) {
                boolean immediate = true;
                for (int other = strictPostDominators.nextSetBit(0);
                     other >= 0;
                     other = strictPostDominators.nextSetBit(other + 1)) {
                    if (other != candidate && !postDominators[candidate].get(other)) {
                        immediate = false;
                        break;
                    }
                }
                if (immediate) {
                    ipdoms[id] = candidate == blockCount ? syntheticExit : cfg.blocks().get(candidate);
                    break;
                }
            }
        }
        return ipdoms;
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] computeChildren(Cfg cfg, int blockCount, int syntheticExitId, BasicBlock[] ipdoms) {
        ArrayList<BasicBlock>[] children = new ArrayList[blockCount + 1];
        for (int i = 0; i < children.length; i++) {
            children[i] = new ArrayList<>();
        }
        for (int id = 0; id < blockCount; id++) {
            BasicBlock ipdom = ipdoms[id];
            if (ipdom != null) {
                int parentId = ipdom.id() == syntheticExitId ? syntheticExitId : ipdom.id();
                children[parentId].add(cfg.blocks().get(id));
            }
        }
        List<BasicBlock>[] output = new List[children.length];
        for (int i = 0; i < output.length; i++) {
            ArrayList<BasicBlock> filtered = new ArrayList<>();
            for (BasicBlock child : children[i]) {
                if (child != null) {
                    filtered.add(child);
                }
            }
            output[i] = Collections.unmodifiableList(filtered);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private List<BasicBlock>[] computePostDominanceFrontiers(Cfg cfg, BasicBlock[] ipdoms) {
        LinkedHashSet<BasicBlock>[] frontiers = new LinkedHashSet[blockCount + 1];
        for (int i = 0; i < frontiers.length; i++) {
            frontiers[i] = new LinkedHashSet<>();
        }
        for (BasicBlock from : cfg.blocks()) {
            for (Edge edge : from.successors()) {
                BasicBlock runner = edge.to();
                BasicBlock stop = ipdoms[from.id()];
                while (runner != null && runner != stop) {
                    frontiers[runner.id()].add(from);
                    runner = ipdoms[runner.id()];
                }
            }
        }
        return freeze(frontiers);
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
