package io.fiber.net.script.aot;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class RemoveUnreachableBlocksPass implements CfgPass {
    @Override
    public Cfg run(Cfg cfg) {
        Set<BasicBlock> reachable = Collections.newSetFromMap(new IdentityHashMap<BasicBlock, Boolean>());
        ArrayDeque<BasicBlock> queue = new ArrayDeque<>();
        reachable.add(cfg.entryBlock());
        queue.add(cfg.entryBlock());
        while (!queue.isEmpty()) {
            BasicBlock block = queue.poll();
            for (Edge edge : block.successors()) {
                if (reachable.add(edge.to())) {
                    queue.add(edge.to());
                }
            }
        }
        if (reachable.size() == cfg.blocks().size()) {
            return cfg;
        }
        return CfgRewriter.rewrite(cfg, reachable, Collections.<BasicBlock, java.util.List<CfgRewriter.EdgeTarget>>emptyMap());
    }
}
