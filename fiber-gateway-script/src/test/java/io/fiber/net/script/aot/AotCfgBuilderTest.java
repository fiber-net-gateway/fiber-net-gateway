package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class AotCfgBuilderTest {

    @Test
    public void shouldBuildStraightLineBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.LOAD_ROOT,
                Code.END_RETURN
        }, null);

        Assert.assertEquals(2, cfg.codeLength());
        Assert.assertEquals(1, cfg.blocks().size());
        BasicBlock entry = cfg.entryBlock();
        Assert.assertEquals(0, entry.startPc());
        Assert.assertEquals(2, entry.endPc());
        Assert.assertTrue(entry.successors().isEmpty());
        Assert.assertSame(entry, cfg.blockAtPc(0));
        Assert.assertSame(entry, cfg.blockAtPc(1));
    }

    @Test
    public void shouldBuildIfEdges() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        Assert.assertEquals(4, cfg.blocks().size());
        BasicBlock test = cfg.blocks().get(0);
        BasicBlock trueBlock = cfg.blocks().get(1);
        BasicBlock falseBlock = cfg.blocks().get(2);
        BasicBlock end = cfg.blocks().get(3);

        assertBlock(test, 0, 2);
        assertBlock(trueBlock, 2, 4);
        assertBlock(falseBlock, 4, 5);
        assertBlock(end, 5, 6);

        assertEdge(test, falseBlock, EdgeKind.COND_FALSE);
        assertEdge(test, trueBlock, EdgeKind.COND_TRUE);
        assertEdge(trueBlock, end, EdgeKind.JUMP);
        assertEdge(falseBlock, end, EdgeKind.FALLTHROUGH);
        Assert.assertEquals(2, end.predecessors().size());
    }

    @Test
    public void shouldBuildLoopBackEdge() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.ITERATE_NEXT,
                (4 << 8) | Code.JUMP_IF_FALSE,
                Code.NOOP,
                (0 << 8) | Code.JUMP,
                Code.END_RETURN
        }, null);

        Assert.assertEquals(3, cfg.blocks().size());
        BasicBlock loopHead = cfg.blocks().get(0);
        BasicBlock body = cfg.blocks().get(1);
        BasicBlock end = cfg.blocks().get(2);

        assertBlock(loopHead, 0, 2);
        assertBlock(body, 2, 4);
        assertBlock(end, 4, 5);

        assertEdge(loopHead, body, EdgeKind.COND_TRUE);
        assertEdge(loopHead, end, EdgeKind.COND_FALSE);
        assertEdge(body, loopHead, EdgeKind.JUMP);
    }

    @Test
    public void shouldBuildExceptionRegionAndExceptionEdge() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (2 << 16) | Code.CALL_FUNC,
                (4 << 8) | Code.JUMP,
                (0 << 8) | Code.INTO_CATCH,
                Code.END_RETURN,
                Code.END_RETURN
        }, new int[]{0, 2, 4});

        Assert.assertEquals(3, cfg.blocks().size());
        Assert.assertEquals(1, cfg.exceptionRegions().size());

        BasicBlock tryBlock = cfg.blocks().get(0);
        BasicBlock catchBlock = cfg.blocks().get(1);
        BasicBlock afterCatch = cfg.blocks().get(2);

        assertBlock(tryBlock, 0, 2);
        assertBlock(catchBlock, 2, 4);
        assertBlock(afterCatch, 4, 5);

        ExceptionRegion region = cfg.exceptionRegions().get(0);
        Assert.assertEquals(0, region.tryStartPc());
        Assert.assertEquals(2, region.tryEndPc());
        Assert.assertEquals(2, region.catchStartPc());
        Assert.assertEquals(4, region.catchEndPc());
        Assert.assertSame(catchBlock, region.catchBlock());

        assertEdge(tryBlock, afterCatch, EdgeKind.JUMP);
        assertEdge(tryBlock, catchBlock, EdgeKind.EXCEPTION);
        Assert.assertTrue(catchBlock.successors().isEmpty());
    }

    @Test
    public void shouldSplitAtCatchEndWithoutAddingCatchEndEdge() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.BOP_DIVIDE,
                (4 << 8) | Code.JUMP,
                (0 << 8) | Code.INTO_CATCH,
                Code.NOOP,
                Code.END_RETURN
        }, new int[]{0, 2, 4});

        Assert.assertEquals(3, cfg.blocks().size());
        assertBlock(cfg.blocks().get(0), 0, 2);
        assertBlock(cfg.blocks().get(1), 2, 4);
        assertBlock(cfg.blocks().get(2), 4, 5);
        assertEdge(cfg.blocks().get(0), cfg.blocks().get(1), EdgeKind.EXCEPTION);
        assertEdge(cfg.blocks().get(1), cfg.blocks().get(2), EdgeKind.FALLTHROUGH);
    }

    @Test
    public void shouldRejectInvalidJumpTarget() {
        try {
            new AotCfgBuilder().build(new int[]{
                    (8 << 8) | Code.JUMP
            }, null);
            Assert.fail("expected cfg exception");
        } catch (CfgException expected) {
            Assert.assertTrue(expected.getMessage().contains("jump target out of range"));
        }
    }

    @Test
    public void shouldRejectInvalidExceptionTable() {
        try {
            new AotCfgBuilder().build(new int[]{
                    Code.LOAD_ROOT,
                    Code.END_RETURN
            }, new int[]{0, 1});
            Assert.fail("expected cfg exception");
        } catch (CfgException expected) {
            Assert.assertTrue(expected.getMessage().contains("multiple of 3"));
        }

        try {
            new AotCfgBuilder().build(new int[]{
                    Code.LOAD_ROOT,
                    Code.END_RETURN
            }, new int[]{0, 1, 3});
            Assert.fail("expected cfg exception");
        } catch (CfgException expected) {
            Assert.assertTrue(expected.getMessage().contains("out of bounds"));
        }
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
