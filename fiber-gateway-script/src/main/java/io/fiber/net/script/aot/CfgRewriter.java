package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CfgRewriter {
    private CfgRewriter() {
    }

    static Cfg rewrite(Cfg cfg,
                       Set<BasicBlock> retained,
                       Map<BasicBlock, List<EdgeTarget>> edgeOverrides) {
        if (retained.size() == cfg.blocks().size() && edgeOverrides.isEmpty()) {
            return cfg;
        }
        ArrayList<BlockSpec> specs = new ArrayList<>(retained.size());
        for (BasicBlock block : cfg.blocks()) {
            if (retained.contains(block)) {
                specs.add(new BlockSpec(Collections.singletonList(block), block.startPc(), block.endPc(), block.codes()));
            }
        }
        return rebuild(cfg, specs, edgeOverrides);
    }

    static Cfg rebuild(Cfg cfg, List<BlockSpec> specs, Map<BasicBlock, List<EdgeTarget>> edgeOverrides) {
        ArrayList<BasicBlock> blocks = new ArrayList<>(specs.size());
        IdentityHashMap<BasicBlock, BasicBlock> blockMap = new IdentityHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            BlockSpec spec = specs.get(i);
            BasicBlock block = new BasicBlock(i, spec.startPc, spec.endPc, spec.codes);
            blocks.add(block);
            for (BasicBlock old : spec.members) {
                blockMap.put(old, block);
            }
        }

        for (BlockSpec spec : specs) {
            BasicBlock from = blockMap.get(spec.members.get(0));
            List<EdgeTarget> override = overrideFor(spec.members, edgeOverrides);
            if (override != null) {
                for (EdgeTarget target : override) {
                    BasicBlock to = blockMap.get(target.to);
                    if (to != null) {
                        from.addEdgeTo(to, target.kind);
                    }
                }
                continue;
            }
            BasicBlock tail = spec.members.get(spec.members.size() - 1);
            for (Edge edge : tail.successors()) {
                BasicBlock to = blockMap.get(edge.to());
                if (to != null) {
                    from.addEdgeTo(to, edge.kind());
                }
            }
        }

        BasicBlock[] blockByPc = new BasicBlock[cfg.codeLength()];
        for (BasicBlock block : blocks) {
            for (Bytecode code : block.codes()) {
                if (code.pc() >= 0 && code.pc() < blockByPc.length) {
                    blockByPc[code.pc()] = block;
                }
            }
        }

        List<ExceptionRegion> regions = rewriteExceptionRegions(cfg, blockMap);
        return new Cfg(cfg.bytecodes(), blocks, blockByPc, regions);
    }

    private static List<EdgeTarget> overrideFor(List<BasicBlock> members,
                                                Map<BasicBlock, List<EdgeTarget>> edgeOverrides) {
        for (int i = members.size() - 1; i >= 0; i--) {
            List<EdgeTarget> override = edgeOverrides.get(members.get(i));
            if (override != null) {
                return override;
            }
        }
        return null;
    }

    private static List<ExceptionRegion> rewriteExceptionRegions(Cfg cfg,
                                                                 IdentityHashMap<BasicBlock, BasicBlock> blockMap) {
        if (cfg.exceptionRegions().isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<ExceptionRegion> regions = new ArrayList<>(cfg.exceptionRegions().size());
        HashSet<String> seen = new HashSet<>();
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            BasicBlock catchBlock = blockMap.get(region.catchBlock());
            if (catchBlock == null || !hasRetainedTryBlock(cfg, region, blockMap)) {
                continue;
            }
            String key = region.tryStartPc() + ":" + region.tryEndPc() + ":"
                    + region.catchStartPc() + ":" + region.catchEndPc() + ":" + catchBlock.id();
            if (seen.add(key)) {
                regions.add(new ExceptionRegion(region.tryStartPc(), region.tryEndPc(),
                        region.catchStartPc(), region.catchEndPc(), catchBlock));
            }
        }
        return regions;
    }

    private static boolean hasRetainedTryBlock(Cfg cfg,
                                               ExceptionRegion region,
                                               IdentityHashMap<BasicBlock, BasicBlock> blockMap) {
        for (BasicBlock block : cfg.blocks()) {
            if (!blockMap.containsKey(block)) {
                continue;
            }
            if (block.startPc() < region.tryEndPc() && block.endPc() > region.tryStartPc()) {
                return true;
            }
        }
        return false;
    }

    static Set<BasicBlock> allBlocks(Cfg cfg) {
        Set<BasicBlock> blocks = Collections.newSetFromMap(new IdentityHashMap<BasicBlock, Boolean>());
        blocks.addAll(cfg.blocks());
        return blocks;
    }

    static final class BlockSpec {
        private final List<BasicBlock> members;
        private final int startPc;
        private final int endPc;
        private final Bytecode[] codes;

        BlockSpec(List<BasicBlock> members, int startPc, int endPc, Bytecode[] codes) {
            this.members = members;
            this.startPc = startPc;
            this.endPc = endPc;
            this.codes = codes;
        }
    }

    static final class EdgeTarget {
        final BasicBlock to;
        final EdgeKind kind;

        EdgeTarget(BasicBlock to, EdgeKind kind) {
            this.to = to;
            this.kind = kind;
        }
    }
}
