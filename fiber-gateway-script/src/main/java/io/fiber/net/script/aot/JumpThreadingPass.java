package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Compares;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JumpThreadingPass {
    public Cfg run(Cfg cfg, SsaGraph graph) {
        Map<BasicBlock, List<CfgRewriter.EdgeTarget>> overrides = new IdentityHashMap<>();
        for (SsaBlock join : graph.blocks()) {
            if (join.basicBlock().predecessors().size() < 2 || !isThreadableJoin(cfg, join)) {
                continue;
            }
            SsaValue branch = lastInstruction(join);
            SsaValue condition = branch.operands().isEmpty() ? null : branch.operands().get(0);
            BasicBlock trueTarget = target(join.basicBlock(), EdgeKind.COND_TRUE);
            BasicBlock falseTarget = target(join.basicBlock(), EdgeKind.COND_FALSE);
            if (condition == null || trueTarget == null || falseTarget == null) {
                continue;
            }
            for (Edge incoming : join.basicBlock().predecessors()) {
                if (incoming.kind() == EdgeKind.EXCEPTION) {
                    continue;
                }
                SsaBlock predecessor = graph.blockFor(incoming.from());
                if (predecessor == null) {
                    continue;
                }
                Boolean value = evaluateCondition(graph, predecessor, join, condition);
                if (value != null) {
                    BasicBlock threadedTarget = value ? trueTarget : falseTarget;
                    putThreadedEdge(overrides, incoming, threadedTarget);
                }
            }
        }
        if (overrides.isEmpty()) {
            return cfg;
        }
        Set<BasicBlock> retained = CfgRewriter.allBlocks(cfg);
        return CfgRewriter.rewrite(cfg, retained, overrides);
    }

    private static boolean isThreadableJoin(Cfg cfg, SsaBlock join) {
        if (isCatchBlock(cfg, join.basicBlock()) || join.instructions().isEmpty()) {
            return false;
        }
        SsaValue last = lastInstruction(join);
        if (last.kind() != SsaKind.BRANCH) {
            return false;
        }
        for (int i = 0; i < join.instructions().size() - 1; i++) {
            if (!isThreadableInstruction(join.instructions().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isThreadableInstruction(SsaValue value) {
        switch (value.kind()) {
            case CONST:
            case LOAD_VAR:
            case DUP:
            case BINARY:
            case UNARY:
                return value.bytecode() == null || !value.bytecode().mayThrow();
            default:
                return false;
        }
    }

    private static boolean isCatchBlock(Cfg cfg, BasicBlock block) {
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            if (region.catchBlock() == block) {
                return true;
            }
        }
        return false;
    }

    private static Boolean evaluateCondition(SsaGraph graph, SsaBlock predecessor, SsaBlock join, SsaValue condition) {
        JsonNode value = evaluate(graph, predecessor, join, condition, 0);
        return value == null ? null : Compares.logic(value);
    }

    private static JsonNode evaluate(SsaGraph graph,
                                     SsaBlock predecessor,
                                     SsaBlock join,
                                     SsaValue value,
                                     int depth) {
        if (value == null || depth > 16) {
            return null;
        }
        if (SsaEffects.isCopy(value)) {
            return evaluate(graph, predecessor, join, value.operands().get(0), depth + 1);
        }
        if (value.kind() == SsaKind.PHI) {
            SsaValue incoming = incomingValue(predecessor, join, value);
            return incoming == value ? null : evaluate(graph, predecessor, join, incoming, depth + 1);
        }
        if (value.kind() == SsaKind.CONST) {
            Object constant = graph.constant(value.index());
            return constant instanceof JsonNode ? (JsonNode) constant : null;
        }
        if (value.kind() == SsaKind.BINARY && value.bytecode() != null) {
            JsonNode left = evaluate(graph, predecessor, join, value.operands().get(0), depth + 1);
            JsonNode right = evaluate(graph, predecessor, join, value.operands().get(1), depth + 1);
            return foldBinary(value.bytecode().op(), left, right);
        }
        return null;
    }

    private static SsaValue incomingValue(SsaBlock predecessor, SsaBlock join, SsaValue phi) {
        VarState joinVars = join.inVars();
        VarState predVars = predecessor.outVars();
        if (joinVars != null && predVars != null && phi.index() >= 0 && phi.index() < joinVars.size()
                && joinVars.get(phi.index()) == phi && phi.index() < predVars.size()) {
            return predVars.get(phi.index());
        }
        StackState joinStack = join.inStack();
        StackState predStack = predecessor.outStack();
        if (joinStack != null && predStack != null) {
            for (int i = 0; i < joinStack.size() && i < predStack.size(); i++) {
                if (joinStack.get(i) == phi) {
                    return predStack.get(i);
                }
            }
        }
        return null;
    }

    private static JsonNode foldBinary(Op op, JsonNode left, JsonNode right) {
        if (left == null || right == null) {
            return null;
        }
        try {
            switch (op) {
                case BOP_LT:
                    return Binaries.lt(left, right);
                case BOP_LTE:
                    return Binaries.lte(left, right);
                case BOP_GT:
                    return Binaries.gt(left, right);
                case BOP_GTE:
                    return Binaries.gte(left, right);
                case BOP_EQ:
                    return Binaries.eq(left, right);
                case BOP_SEQ:
                    return Binaries.seq(left, right);
                case BOP_NE:
                    return Binaries.ne(left, right);
                case BOP_SNE:
                    return Binaries.sne(left, right);
                default:
                    return null;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void putThreadedEdge(Map<BasicBlock, List<CfgRewriter.EdgeTarget>> overrides,
                                        Edge incoming,
                                        BasicBlock threadedTarget) {
        BasicBlock from = incoming.from();
        List<CfgRewriter.EdgeTarget> targets = overrides.get(from);
        if (targets == null) {
            targets = new ArrayList<>(from.successors().size());
            for (Edge edge : from.successors()) {
                targets.add(new CfgRewriter.EdgeTarget(edge.to(), edge.kind()));
            }
            overrides.put(from, targets);
        }
        for (int i = 0; i < targets.size(); i++) {
            CfgRewriter.EdgeTarget target = targets.get(i);
            if (target.to == incoming.to() && target.kind == incoming.kind()) {
                targets.set(i, new CfgRewriter.EdgeTarget(threadedTarget, target.kind));
                return;
            }
        }
    }

    private static SsaValue lastInstruction(SsaBlock block) {
        return block.instructions().get(block.instructions().size() - 1);
    }

    private static BasicBlock target(BasicBlock block, EdgeKind kind) {
        for (Edge edge : block.successors()) {
            if (edge.kind() == kind) {
                return edge.to();
            }
        }
        return null;
    }
}
