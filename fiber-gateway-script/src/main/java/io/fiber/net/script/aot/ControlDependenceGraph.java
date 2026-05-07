package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ControlDependenceGraph {
    private final Cfg cfg;
    private final PostDominatorTree postDominatorTree;
    private final List<BasicBlock>[] dependentsByController;
    private final List<BasicBlock>[] controllersByDependent;

    public ControlDependenceGraph(Cfg cfg, PostDominatorTree postDominatorTree) {
        this.cfg = cfg;
        this.postDominatorTree = postDominatorTree;
        Graph graph = compute(cfg, postDominatorTree);
        this.dependentsByController = graph.dependentsByController;
        this.controllersByDependent = graph.controllersByDependent;
    }

    public Cfg cfg() {
        return cfg;
    }

    public PostDominatorTree postDominatorTree() {
        return postDominatorTree;
    }

    public List<BasicBlock> controlDependents(BasicBlock controller) {
        if (controller == null || controller.id() < 0 || controller.id() >= dependentsByController.length) {
            return Collections.emptyList();
        }
        return dependentsByController[controller.id()];
    }

    public List<BasicBlock> controllers(BasicBlock dependent) {
        if (dependent == null || dependent.id() < 0 || dependent.id() >= controllersByDependent.length) {
            return Collections.emptyList();
        }
        return controllersByDependent[dependent.id()];
    }

    @SuppressWarnings("unchecked")
    private static Graph compute(Cfg cfg, PostDominatorTree postDominatorTree) {
        int size = cfg.blocks().size();
        LinkedHashSet<BasicBlock>[] dependents = new LinkedHashSet[size];
        LinkedHashSet<BasicBlock>[] controllers = new LinkedHashSet[size];
        for (int i = 0; i < size; i++) {
            dependents[i] = new LinkedHashSet<>();
            controllers[i] = new LinkedHashSet<>();
        }

        for (BasicBlock controller : cfg.blocks()) {
            BasicBlock stop = postDominatorTree.immediatePostDominator(controller);
            for (Edge edge : controller.successors()) {
                BasicBlock runner = edge.to();
                if (postDominatorTree.postDominates(runner, controller)) {
                    continue;
                }
                while (runner != null && runner != stop && runner != postDominatorTree.syntheticExit()) {
                    dependents[controller.id()].add(runner);
                    controllers[runner.id()].add(controller);
                    runner = postDominatorTree.immediatePostDominator(runner);
                }
            }
        }

        return new Graph(freeze(dependents), freeze(controllers));
    }

    @SuppressWarnings("unchecked")
    private static List<BasicBlock>[] freeze(Set<BasicBlock>[] input) {
        List<BasicBlock>[] output = new List[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = Collections.unmodifiableList(new ArrayList<>(input[i]));
        }
        return output;
    }

    private static final class Graph {
        private final List<BasicBlock>[] dependentsByController;
        private final List<BasicBlock>[] controllersByDependent;

        private Graph(List<BasicBlock>[] dependentsByController, List<BasicBlock>[] controllersByDependent) {
            this.dependentsByController = dependentsByController;
            this.controllersByDependent = controllersByDependent;
        }
    }
}
