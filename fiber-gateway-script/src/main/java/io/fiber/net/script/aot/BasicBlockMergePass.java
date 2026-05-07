package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BasicBlockMergePass implements CfgPass {
    @Override
    public Cfg run(Cfg cfg) {
        ArrayList<CfgRewriter.BlockSpec> specs = new ArrayList<>(cfg.blocks().size());
        boolean changed = false;
        for (int i = 0; i < cfg.blocks().size(); i++) {
            BasicBlock block = cfg.blocks().get(i);
            BasicBlock mergeTarget = mergeTarget(cfg, block);
            if (mergeTarget != null && (i + 1 >= cfg.blocks().size() || cfg.blocks().get(i + 1) != mergeTarget)) {
                mergeTarget = null;
            }
            if (mergeTarget == null) {
                specs.add(new CfgRewriter.BlockSpec(Collections.singletonList(block),
                        block.startPc(), block.endPc(), block.codes()));
                continue;
            }
            specs.add(new CfgRewriter.BlockSpec(two(block, mergeTarget),
                    block.startPc(), mergeTarget.endPc(), mergedCodes(block, mergeTarget)));
            changed = true;
            i++;
        }
        if (!changed) {
            return cfg;
        }
        return CfgRewriter.rebuild(cfg, specs, Collections.<BasicBlock, List<CfgRewriter.EdgeTarget>>emptyMap());
    }

    private static BasicBlock mergeTarget(Cfg cfg, BasicBlock block) {
        if (block.successors().size() != 1) {
            return null;
        }
        Edge edge = block.successors().get(0);
        if (edge.kind() == EdgeKind.EXCEPTION) {
            return null;
        }
        BasicBlock target = edge.to();
        if (block.endPc() != target.startPc() || target.predecessors().size() != 1) {
            return null;
        }
        if (isCatchBlock(cfg, target) || crossesExceptionBoundary(cfg, block, target)) {
            return null;
        }
        Bytecode last = block.lastCode();
        return edge.kind() == EdgeKind.FALLTHROUGH
                || (last.op() == Op.JUMP && last.targetPc() == target.startPc()) ? target : null;
    }

    private static boolean isCatchBlock(Cfg cfg, BasicBlock block) {
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            if (region.catchBlock() == block) {
                return true;
            }
        }
        return false;
    }

    private static boolean crossesExceptionBoundary(Cfg cfg, BasicBlock left, BasicBlock right) {
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            if (region.containsTryPc(left.startPc()) != region.containsTryPc(right.startPc())) {
                return true;
            }
            if (region.catchBlock() == left || region.catchBlock() == right) {
                return true;
            }
        }
        return false;
    }

    private static List<BasicBlock> two(BasicBlock left, BasicBlock right) {
        ArrayList<BasicBlock> blocks = new ArrayList<>(2);
        blocks.add(left);
        blocks.add(right);
        return blocks;
    }

    private static Bytecode[] mergedCodes(BasicBlock left, BasicBlock right) {
        Bytecode[] leftCodes = left.codes();
        Bytecode[] rightCodes = right.codes();
        int leftLength = left.lastCode().op() == Op.JUMP && left.lastCode().targetPc() == right.startPc()
                ? leftCodes.length - 1 : leftCodes.length;
        Bytecode[] merged = new Bytecode[leftLength + rightCodes.length];
        System.arraycopy(leftCodes, 0, merged, 0, leftLength);
        System.arraycopy(rightCodes, 0, merged, leftLength, rightCodes.length);
        return merged;
    }
}
