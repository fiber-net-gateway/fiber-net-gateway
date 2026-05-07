package io.fiber.net.script.aot;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class CfgCompactorTest {
    @Test
    public void shouldCompactUnreachableBlockAndRemoveJumpToNextBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (2 << 8) | Code.JUMP,
                Code.NOOP,
                Code.END_RETURN
        }, null);
        Cfg optimized = new RemoveUnreachableBlocksPass().run(cfg);

        CompactBytecode compact = new CfgCompactor().compact(optimized);

        Assert.assertArrayEquals(new int[]{Code.END_RETURN}, compact.codes());
        Assert.assertEquals(Integer.valueOf(0), compact.oldPcToNewPc().get(2));
        Cfg rebuilt = new AotCfgBuilder().build(compact.codes(), compact.exceptionTable());
        Assert.assertEquals(1, rebuilt.blocks().size());
    }

    @Test
    public void shouldRemapJumpTargetAfterRemovingDeadBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (3 << 8) | Code.JUMP,
                Code.NOOP,
                Code.NOOP,
                Code.END_RETURN
        }, null);
        Cfg optimized = new RemoveUnreachableBlocksPass().run(cfg);

        CompactBytecode compact = new CfgCompactor().compact(optimized);

        Assert.assertArrayEquals(new int[]{Code.END_RETURN}, compact.codes());
        Assert.assertEquals(Integer.valueOf(0), compact.oldPcToNewPc().get(3));
    }

    @Test
    public void shouldPreserveStackBalanceWhenBranchBecomesSingleEdge() {
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

        CompactBytecode compact = new CfgCompactor().compact(optimized);

        Assert.assertArrayEquals(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                Code.POP,
                (1 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, compact.codes());
        Cfg rebuilt = new AotCfgBuilder().build(compact.codes(), compact.exceptionTable());
        Assert.assertEquals(1, rebuilt.blocks().size());
    }

    @Test
    public void shouldCompactThreadedCfgAndRemapTargets() {
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
        Object[] constants = {
                BooleanNode.TRUE,
                IntNode.valueOf(1),
                IntNode.valueOf(2),
                IntNode.valueOf(10),
                IntNode.valueOf(20)
        };
        SsaGraph graph = new SsaBuilder().build(cfg, 1, constants);
        Cfg threaded = new JumpThreadingPass().run(cfg, graph);
        Cfg optimized = new RemoveUnreachableBlocksPass().run(threaded);

        CompactBytecode compact = new CfgCompactor().compact(optimized);
        Cfg rebuilt = new AotCfgBuilder().build(compact.codes(), compact.exceptionTable());

        assertNoOpcode(compact.codes(), Code.BOP_EQ);
        assertNoOpcode(compact.codes(), Code.LOAD_VAR);
        Assert.assertEquals(5, rebuilt.blocks().size());
        assertEdge(rebuilt.blocks().get(1), rebuilt.blocks().get(3), EdgeKind.JUMP);
        assertEdge(rebuilt.blocks().get(2), rebuilt.blocks().get(4), EdgeKind.JUMP);
    }

    @Test
    public void shouldRemapExceptionTable() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.BOP_DIVIDE,
                (4 << 8) | Code.JUMP,
                (0 << 8) | Code.INTO_CATCH,
                Code.NOOP,
                Code.END_RETURN
        }, new int[]{0, 2, 4});

        CompactBytecode compact = new CfgCompactor().compact(cfg);

        Assert.assertArrayEquals(new int[]{0, 2, 4}, compact.exceptionTable());
        Cfg rebuilt = new AotCfgBuilder().build(compact.codes(), compact.exceptionTable());
        Assert.assertEquals(1, rebuilt.exceptionRegions().size());
        assertEdge(rebuilt.blocks().get(0), rebuilt.blocks().get(1), EdgeKind.EXCEPTION);
    }

    private static void assertNoOpcode(int[] codes, int opcode) {
        for (int code : codes) {
            Assert.assertNotEquals(opcode, code & 0xFF);
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
}
