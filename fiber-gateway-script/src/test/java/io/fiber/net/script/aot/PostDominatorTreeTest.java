package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class PostDominatorTreeTest {

    @Test
    public void shouldComputePostDominatorsForIfJoin() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        PostDominatorTree tree = new PostDominatorTree(cfg);
        BasicBlock test = cfg.blocks().get(0);
        BasicBlock trueBlock = cfg.blocks().get(1);
        BasicBlock falseBlock = cfg.blocks().get(2);
        BasicBlock end = cfg.blocks().get(3);

        Assert.assertEquals(1, tree.exitBlocks().size());
        Assert.assertSame(end, tree.exitBlocks().get(0));
        Assert.assertTrue(tree.postDominates(end, test));
        Assert.assertTrue(tree.postDominates(end, trueBlock));
        Assert.assertTrue(tree.postDominates(end, falseBlock));
        Assert.assertSame(end, tree.immediatePostDominator(test));
        Assert.assertSame(end, tree.immediatePostDominator(trueBlock));
        Assert.assertSame(end, tree.immediatePostDominator(falseBlock));
        Assert.assertSame(tree.syntheticExit(), tree.immediatePostDominator(end));
    }

    @Test
    public void shouldComputeControlDependenceForIfAndLoop() {
        Cfg ifCfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        ControlDependenceGraph ifCdg = new ControlDependenceGraph(ifCfg, new PostDominatorTree(ifCfg));
        BasicBlock test = ifCfg.blocks().get(0);
        BasicBlock trueBlock = ifCfg.blocks().get(1);
        BasicBlock falseBlock = ifCfg.blocks().get(2);
        BasicBlock end = ifCfg.blocks().get(3);

        Assert.assertTrue(ifCdg.controllers(trueBlock).contains(test));
        Assert.assertTrue(ifCdg.controllers(falseBlock).contains(test));
        Assert.assertFalse(ifCdg.controllers(end).contains(test));

        Cfg loopCfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.ITERATE_NEXT,
                (4 << 8) | Code.JUMP_IF_FALSE,
                Code.NOOP,
                (0 << 8) | Code.JUMP,
                Code.END_RETURN
        }, null);

        ControlDependenceGraph loopCdg = new ControlDependenceGraph(loopCfg, new PostDominatorTree(loopCfg));
        BasicBlock header = loopCfg.blocks().get(0);
        BasicBlock body = loopCfg.blocks().get(1);
        Assert.assertTrue(loopCdg.controllers(body).contains(header));
    }
}
