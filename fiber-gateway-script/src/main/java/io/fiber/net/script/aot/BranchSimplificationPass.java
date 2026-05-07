package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.run.Compares;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BranchSimplificationPass implements CfgPass {
    private final Object[] constants;

    public BranchSimplificationPass() {
        this(null);
    }

    public BranchSimplificationPass(Object[] constants) {
        this.constants = constants;
    }

    @Override
    public Cfg run(Cfg cfg) {
        Map<BasicBlock, List<CfgRewriter.EdgeTarget>> overrides = new IdentityHashMap<>();
        for (BasicBlock block : cfg.blocks()) {
            if (!isBranchBlock(block)) {
                continue;
            }
            BasicBlock trueTarget = target(block, EdgeKind.COND_TRUE);
            BasicBlock falseTarget = target(block, EdgeKind.COND_FALSE);
            if (trueTarget == null || falseTarget == null) {
                continue;
            }
            Boolean constant = constantCondition(block);
            if (constant != null) {
                overrides.put(block, single(constant ? trueTarget : falseTarget));
            } else if (trueTarget == falseTarget) {
                overrides.put(block, single(trueTarget));
            }
        }
        if (overrides.isEmpty()) {
            return cfg;
        }
        Set<BasicBlock> retained = CfgRewriter.allBlocks(cfg);
        return CfgRewriter.rewrite(cfg, retained, overrides);
    }

    private static boolean isBranchBlock(BasicBlock block) {
        if (block.codeLength() == 0) {
            return false;
        }
        Op op = block.lastCode().op();
        return op == Op.JUMP_IF_FALSE || op == Op.JUMP_IF_TRUE;
    }

    private Boolean constantCondition(BasicBlock block) {
        if (constants == null || block.codeLength() < 2) {
            return null;
        }
        Bytecode previous = block.codeAt(block.codeLength() - 2);
        if (previous.op() != Op.LOAD_CONST || previous.operandIndex() < 0 || previous.operandIndex() >= constants.length) {
            return null;
        }
        Object constant = constants[previous.operandIndex()];
        if (!(constant instanceof JsonNode)) {
            return null;
        }
        return Compares.logic((JsonNode) constant);
    }

    private static BasicBlock target(BasicBlock block, EdgeKind kind) {
        for (Edge edge : block.successors()) {
            if (edge.kind() == kind) {
                return edge.to();
            }
        }
        return null;
    }

    private static List<CfgRewriter.EdgeTarget> single(BasicBlock target) {
        ArrayList<CfgRewriter.EdgeTarget> targets = new ArrayList<>(1);
        targets.add(new CfgRewriter.EdgeTarget(target, EdgeKind.JUMP));
        return targets;
    }
}
