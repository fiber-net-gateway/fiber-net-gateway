package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LoopInfo {
    private final Cfg cfg;
    private final DominatorTree dominatorTree;
    private final List<Loop> loops;
    private final int[] loopDepthByBlockId;

    public LoopInfo(Cfg cfg, DominatorTree dominatorTree) {
        this.cfg = cfg;
        this.dominatorTree = dominatorTree;
        this.loops = Collections.unmodifiableList(computeLoops(cfg, dominatorTree));
        this.loopDepthByBlockId = computeLoopDepths(cfg, loops);
    }

    public Cfg cfg() {
        return cfg;
    }

    public DominatorTree dominatorTree() {
        return dominatorTree;
    }

    public List<Loop> loops() {
        return loops;
    }

    public int loopDepth(BasicBlock block) {
        if (block == null || block.id() < 0 || block.id() >= loopDepthByBlockId.length) {
            return 0;
        }
        return loopDepthByBlockId[block.id()];
    }

    public Loop loopForHeader(BasicBlock header) {
        if (header == null) {
            return null;
        }
        for (Loop loop : loops) {
            if (loop.header() == header) {
                return loop;
            }
        }
        return null;
    }

    private static List<Loop> computeLoops(Cfg cfg, DominatorTree dominatorTree) {
        ArrayList<LoopBuilder> builders = new ArrayList<>();
        for (BasicBlock block : cfg.blocks()) {
            for (Edge edge : block.successors()) {
                if (edge.kind() == EdgeKind.EXCEPTION) {
                    continue;
                }
                BasicBlock header = edge.to();
                BasicBlock latch = edge.from();
                if (!dominatorTree.dominates(header, latch)) {
                    continue;
                }
                LoopBuilder builder = builderFor(builders, header);
                builder.latches.add(latch);
                builder.backEdges.add(edge);
                collectNaturalLoop(builder.blocks, header, latch, dominatorTree);
            }
        }

        ArrayList<Loop> loops = new ArrayList<>(builders.size());
        for (LoopBuilder builder : builders) {
            LinkedHashSet<BasicBlock> exitBlocks = new LinkedHashSet<>();
            LinkedHashSet<BasicBlock> exitTargets = new LinkedHashSet<>();
            LinkedHashSet<Edge> exitEdges = new LinkedHashSet<>();
            for (BasicBlock block : orderedBlocks(cfg, builder.blocks)) {
                for (Edge edge : block.successors()) {
                    if (edge.kind() == EdgeKind.EXCEPTION) {
                        continue;
                    }
                    if (!builder.blocks.contains(edge.to())) {
                        exitBlocks.add(block);
                        exitTargets.add(edge.to());
                        exitEdges.add(edge);
                    }
                }
            }
            loops.add(new Loop(
                    builder.header,
                    orderedBlocks(cfg, builder.blocks),
                    orderedBlocks(cfg, builder.latches),
                    orderedBlocks(cfg, exitBlocks),
                    orderedBlocks(cfg, exitTargets),
                    findPreHeader(builder.header, builder.blocks),
                    new ArrayList<>(builder.backEdges),
                    new ArrayList<>(exitEdges)
            ));
        }
        return loops;
    }

    private static LoopBuilder builderFor(ArrayList<LoopBuilder> builders, BasicBlock header) {
        for (LoopBuilder builder : builders) {
            if (builder.header == header) {
                return builder;
            }
        }
        LoopBuilder builder = new LoopBuilder(header);
        builders.add(builder);
        return builder;
    }

    private static void collectNaturalLoop(Set<BasicBlock> loop,
                                           BasicBlock header,
                                           BasicBlock latch,
                                           DominatorTree dominatorTree) {
        loop.add(header);
        if (!loop.add(latch)) {
            return;
        }
        ArrayDeque<BasicBlock> stack = new ArrayDeque<>();
        stack.push(latch);
        while (!stack.isEmpty()) {
            BasicBlock block = stack.pop();
            for (Edge edge : block.predecessors()) {
                if (edge.kind() == EdgeKind.EXCEPTION) {
                    continue;
                }
                BasicBlock pred = edge.from();
                if (pred != header && !dominatorTree.dominates(header, pred)) {
                    continue;
                }
                if (loop.add(pred)) {
                    stack.push(pred);
                }
            }
        }
    }

    private static int[] computeLoopDepths(Cfg cfg, List<Loop> loops) {
        int[] depths = new int[cfg.blocks().size()];
        for (Loop loop : loops) {
            for (BasicBlock block : loop.blocks()) {
                depths[block.id()]++;
            }
        }
        return depths;
    }

    private static List<BasicBlock> orderedBlocks(Cfg cfg, Set<BasicBlock> blocks) {
        ArrayList<BasicBlock> ordered = new ArrayList<>(blocks.size());
        for (BasicBlock block : cfg.blocks()) {
            if (blocks.contains(block)) {
                ordered.add(block);
            }
        }
        return ordered;
    }

    private static BasicBlock findPreHeader(BasicBlock header, Set<BasicBlock> loopBlocks) {
        BasicBlock preHeader = null;
        for (Edge edge : header.predecessors()) {
            BasicBlock pred = edge.from();
            if (loopBlocks.contains(pred)) {
                continue;
            }
            if (preHeader != null) {
                return null;
            }
            preHeader = pred;
        }
        return preHeader;
    }

    private static final class LoopBuilder {
        private final BasicBlock header;
        private final LinkedHashSet<BasicBlock> blocks = new LinkedHashSet<>();
        private final LinkedHashSet<BasicBlock> latches = new LinkedHashSet<>();
        private final LinkedHashSet<Edge> backEdges = new LinkedHashSet<>();

        private LoopBuilder(BasicBlock header) {
            this.header = header;
        }
    }

    public static final class Loop {
        private final BasicBlock header;
        private final List<BasicBlock> blocks;
        private final List<BasicBlock> latches;
        private final List<BasicBlock> exitBlocks;
        private final List<BasicBlock> exitTargets;
        private final BasicBlock preHeader;
        private final List<Edge> backEdges;
        private final List<Edge> exitEdges;

        private Loop(BasicBlock header,
                     List<BasicBlock> blocks,
                     List<BasicBlock> latches,
                     List<BasicBlock> exitBlocks,
                     List<BasicBlock> exitTargets,
                     BasicBlock preHeader,
                     List<Edge> backEdges,
                     List<Edge> exitEdges) {
            this.header = header;
            this.blocks = Collections.unmodifiableList(blocks);
            this.latches = Collections.unmodifiableList(latches);
            this.exitBlocks = Collections.unmodifiableList(exitBlocks);
            this.exitTargets = Collections.unmodifiableList(exitTargets);
            this.preHeader = preHeader;
            this.backEdges = Collections.unmodifiableList(backEdges);
            this.exitEdges = Collections.unmodifiableList(exitEdges);
        }

        public BasicBlock header() {
            return header;
        }

        public List<BasicBlock> blocks() {
            return blocks;
        }

        public List<BasicBlock> latches() {
            return latches;
        }

        public List<BasicBlock> exitBlocks() {
            return exitBlocks;
        }

        public List<BasicBlock> exitTargets() {
            return exitTargets;
        }

        public BasicBlock preHeader() {
            return preHeader;
        }

        public List<Edge> backEdges() {
            return backEdges;
        }

        public List<Edge> exitEdges() {
            return exitEdges;
        }

        public boolean contains(BasicBlock block) {
            return blocks.contains(block);
        }

        @Override
        public String toString() {
            return "Loop{header=" + header + ", blocks=" + blocks + '}';
        }
    }
}
