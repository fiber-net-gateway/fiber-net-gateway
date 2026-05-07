package io.fiber.net.script.aot;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class CfgOptimizerTest {
    @Test
    public void shouldRemoveUnreachableBlocks() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (2 << 8) | Code.JUMP,
                Code.NOOP,
                Code.END_RETURN
        }, null);

        Cfg optimized = new RemoveUnreachableBlocksPass().run(cfg);

        Assert.assertEquals(2, optimized.blocks().size());
        Assert.assertNull(optimized.blockAtPc(1));
        assertBlock(optimized.blocks().get(0), 0, 1);
        assertBlock(optimized.blocks().get(1), 2, 3);
        assertEdge(optimized.blocks().get(0), optimized.blocks().get(1), EdgeKind.JUMP);
    }

    @Test
    public void shouldMergeAdjacentJumpBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.JUMP,
                Code.LOAD_ROOT,
                Code.END_RETURN
        }, null);

        Cfg optimized = new BasicBlockMergePass().run(cfg);

        Assert.assertEquals(1, optimized.blocks().size());
        Assert.assertEquals(2, optimized.entryBlock().codeLength());
        Assert.assertEquals(Op.LOAD_ROOT, optimized.entryBlock().codeAt(0).op());
        Assert.assertEquals(Op.END_RETURN, optimized.entryBlock().codeAt(1).op());
        Assert.assertTrue(optimized.entryBlock().successors().isEmpty());
    }

    @Test
    public void shouldSimplifyConstantBranchAndRemoveDeadArm() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (1 << 8) | Code.LOAD_CONST,
                Code.END_RETURN,
                (2 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        Cfg optimized = new CfgOptimizer(Arrays.<CfgPass>asList(
                new BranchSimplificationPass(new Object[]{BooleanNode.TRUE, IntNode.valueOf(1), IntNode.valueOf(2)}),
                new RemoveUnreachableBlocksPass()
        )).optimize(cfg);

        Assert.assertEquals(2, optimized.blocks().size());
        Assert.assertNull(optimized.blockAtPc(4));
        assertEdge(optimized.entryBlock(), optimized.blockAtPc(2), EdgeKind.JUMP);
    }

    @Test
    public void shouldThreadJumpThroughPhiConstantComparison() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP_IF_FALSE,
                (1 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (7 << 8) | Code.JUMP,
                (2 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                (13 << 8) | Code.JUMP_IF_FALSE,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN,
                (4 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                BooleanNode.TRUE,
                IntNode.valueOf(1),
                IntNode.valueOf(2),
                IntNode.valueOf(10),
                IntNode.valueOf(20)
        });

        Cfg threaded = new JumpThreadingPass().run(cfg, graph);
        Cfg optimized = new RemoveUnreachableBlocksPass().run(threaded);

        BasicBlock thenBlock = optimized.blockAtPc(2);
        BasicBlock elseBlock = optimized.blockAtPc(5);
        BasicBlock fooBlock = optimized.blockAtPc(11);
        BasicBlock skipBlock = optimized.blockAtPc(13);

        Assert.assertNull(optimized.blockAtPc(7));
        assertEdge(thenBlock, fooBlock, EdgeKind.JUMP);
        assertEdge(elseBlock, skipBlock, EdgeKind.FALLTHROUGH);
    }

    private static void assertEdge(BasicBlock from, BasicBlock to, EdgeKind kind) {
        for (Edge edge : from.successors()) {
            if (edge.to() == to && edge.kind() == kind) {
                return;
            }
        }
        Assert.fail("missing edge " + from + " -" + kind + "-> " + to + ", actual=" + from.successors());
    }

    private static void assertBlock(BasicBlock block, int startPc, int endPc) {
        Assert.assertEquals(startPc, block.startPc());
        Assert.assertEquals(endPc, block.endPc());
    }
}
