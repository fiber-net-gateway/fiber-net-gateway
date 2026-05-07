package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AotAnalysisTest {

    @Test
    public void shouldTrackDefUseChains() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (2 << 8) | Code.LOAD_CONST,
                (7 << 8) | Code.JUMP_IF_FALSE,
                (3 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (9 << 8) | Code.JUMP,
                (4 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);

        SsaGraph graph = new SsaBuilder().build(cfg, 1);
        SsaBlock join = graph.blockFor(cfg.blockAtPc(9));
        SsaValue phi = join.phis().get(0);
        SsaValue load = join.instructions().get(0);

        Assert.assertEquals(SsaKind.LOAD_VAR, load.kind());
        Assert.assertSame(phi, load.operands().get(0));
        assertUse(phi.uses(), phi, load, 0);
        for (int i = 0; i < phi.operands().size(); i++) {
            SsaValue operand = phi.operands().get(i);
            assertUse(operand.uses(), operand, phi, i);
        }
    }

    @Test
    public void shouldComputeDominatorTreeAndFrontier() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        DominatorTree tree = new DominatorTree(cfg);
        BasicBlock test = cfg.blocks().get(0);
        BasicBlock trueBlock = cfg.blocks().get(1);
        BasicBlock falseBlock = cfg.blocks().get(2);
        BasicBlock end = cfg.blocks().get(3);

        Assert.assertNull(tree.immediateDominator(test));
        Assert.assertSame(test, tree.immediateDominator(trueBlock));
        Assert.assertSame(test, tree.immediateDominator(falseBlock));
        Assert.assertSame(test, tree.immediateDominator(end));
        Assert.assertTrue(tree.dominates(test, end));
        Assert.assertFalse(tree.dominates(trueBlock, end));
        Assert.assertTrue(tree.dominanceFrontier(trueBlock).contains(end));
        Assert.assertTrue(tree.dominanceFrontier(falseBlock).contains(end));
    }

    @Test
    public void shouldComputeNaturalLoopInfo() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.ITERATE_NEXT,
                (4 << 8) | Code.JUMP_IF_FALSE,
                Code.NOOP,
                (0 << 8) | Code.JUMP,
                Code.END_RETURN
        }, null);

        DominatorTree tree = new DominatorTree(cfg);
        LoopInfo loopInfo = new LoopInfo(cfg, tree);
        BasicBlock header = cfg.blocks().get(0);
        BasicBlock body = cfg.blocks().get(1);
        BasicBlock end = cfg.blocks().get(2);

        Assert.assertEquals(1, loopInfo.loops().size());
        LoopInfo.Loop loop = loopInfo.loops().get(0);
        Assert.assertSame(header, loop.header());
        Assert.assertEquals(1, loop.backEdges().size());
        Assert.assertTrue(loop.blocks().contains(header));
        Assert.assertTrue(loop.blocks().contains(body));
        Assert.assertFalse(loop.blocks().contains(end));
        Assert.assertEquals(1, loop.latches().size());
        Assert.assertSame(body, loop.latches().get(0));
        Assert.assertNull(loop.preHeader());
        Assert.assertTrue(loop.exitBlocks().contains(header));
        Assert.assertTrue(loop.exitTargets().contains(end));
        Assert.assertEquals(1, loopInfo.loopDepth(header));
        Assert.assertEquals(1, loopInfo.loopDepth(body));
        Assert.assertEquals(0, loopInfo.loopDepth(end));
    }

    @Test
    public void shouldExposeAnalysisFromSsaGraph() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.LOAD_ROOT,
                Code.END_RETURN
        }, null);

        SsaGraph graph = new SsaBuilder().build(cfg, 0);

        Assert.assertNotNull(graph.dominatorTree());
        Assert.assertNotNull(graph.loopInfo());
        Assert.assertSame(cfg, graph.dominatorTree().cfg());
        Assert.assertSame(cfg, graph.loopInfo().cfg());
    }

    private static void assertUse(List<SsaUse> uses, SsaValue value, SsaValue user, int operandIndex) {
        for (SsaUse use : uses) {
            if (use.value() == value && use.user() == user && use.operandIndex() == operandIndex) {
                return;
            }
        }
        Assert.fail("missing use: " + value + " by " + user + '[' + operandIndex + "], actual=" + uses);
    }
}
