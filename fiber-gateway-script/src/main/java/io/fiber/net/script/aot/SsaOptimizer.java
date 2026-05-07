package io.fiber.net.script.aot;

import java.util.Arrays;
import java.util.List;

public final class SsaOptimizer {
    private final List<SsaPass> passes;
    private final int maxIterations;

    public SsaOptimizer(List<SsaPass> passes) {
        this(passes, 8);
    }

    public SsaOptimizer(List<SsaPass> passes, int maxIterations) {
        this.passes = passes;
        this.maxIterations = Math.max(1, maxIterations);
    }

    public static SsaOptimizer conservative() {
        return new SsaOptimizer(Arrays.asList(
                new PhiSimplificationPass(),
                new CopyPropagationPass(),
                new DeadCodeEliminationPass()
        ));
    }

    public static SsaOptimizer defaults() {
        return new SsaOptimizer(Arrays.asList(
                new PhiSimplificationPass(),
                new CopyPropagationPass(),
                new ConstantPropagationPass(),
                new SparseConditionalConstantPropagationPass(),
                new PhiSimplificationPass(),
                new DeadCodeEliminationPass(),
                new AggressiveDeadCodeEliminationPass(),
                new CommonSubexpressionEliminationPass(),
                new GlobalValueNumberingPass(),
                new PhiSimplificationPass(),
                new DeadCodeEliminationPass()
        ));
    }

    public boolean optimize(SsaGraph graph) {
        boolean changed = false;
        for (int i = 0; i < maxIterations; i++) {
            boolean roundChanged = false;
            for (SsaPass pass : passes) {
                roundChanged |= pass.run(graph);
            }
            changed |= roundChanged;
            if (!roundChanged) {
                break;
            }
        }
        return changed;
    }
}
