package io.fiber.net.script.aot;

import java.util.Arrays;
import java.util.List;

public final class CfgOptimizer {
    private final List<CfgPass> passes;
    private final int maxIterations;

    public CfgOptimizer(List<CfgPass> passes) {
        this(passes, 8);
    }

    public CfgOptimizer(List<CfgPass> passes, int maxIterations) {
        this.passes = passes;
        this.maxIterations = Math.max(1, maxIterations);
    }

    public static CfgOptimizer defaults() {
        return new CfgOptimizer(Arrays.asList(
                new RemoveUnreachableBlocksPass(),
                new BranchSimplificationPass(),
                new BasicBlockMergePass(),
                new RemoveUnreachableBlocksPass()
        ));
    }

    public Cfg optimize(Cfg cfg) {
        Cfg current = cfg;
        for (int i = 0; i < maxIterations; i++) {
            Cfg roundStart = current;
            for (CfgPass pass : passes) {
                current = pass.run(current);
            }
            if (current == roundStart) {
                break;
            }
        }
        return current;
    }
}
