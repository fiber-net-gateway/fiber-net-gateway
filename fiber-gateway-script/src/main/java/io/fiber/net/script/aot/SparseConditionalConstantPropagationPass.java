package io.fiber.net.script.aot;

import java.util.BitSet;

public final class SparseConditionalConstantPropagationPass implements SsaPass {
    private BitSet executableBlocks;

    @Override
    public boolean run(SsaGraph graph) {
        executableBlocks = computeExecutableBlocks(graph);
        return new ConstantPropagationPass().run(graph);
    }

    public boolean isExecutable(BasicBlock block) {
        return executableBlocks != null && block != null && executableBlocks.get(block.id());
    }

    private static BitSet computeExecutableBlocks(SsaGraph graph) {
        BitSet reachable = new BitSet(graph.blocks().size());
        for (SsaBlock block : graph.blocks()) {
            if (graph.dominatorTree().isReachable(block.basicBlock())) {
                reachable.set(block.id());
            }
        }
        return reachable;
    }
}
