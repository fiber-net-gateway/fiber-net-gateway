package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class AggressiveDeadCodeEliminationTest {

    @Test
    public void shouldRemoveDeadControlAndItsConditionWhenNoLiveDependentExists() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (1 << 8) | Code.LOAD_CONST,
                Code.POP,
                (2 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0);

        Assert.assertTrue(new AggressiveDeadCodeEliminationPass().run(graph));

        assertNoInstructionKind(graph, SsaKind.BRANCH);
        assertNoInstructionKind(graph, SsaKind.POP);
        SsaValue ret = lastInstruction(graph.blockFor(cfg.blockAtPc(4)));
        Assert.assertEquals(SsaKind.RETURN, ret.kind());
        Assert.assertEquals(SsaKind.CONST, ret.operands().get(0).kind());
    }

    @Test
    public void shouldKeepControlNeededByLiveSideEffect() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (6 << 8) | Code.JUMP_IF_FALSE,
                (1 << 8) | Code.LOAD_CONST,
                (2 << 8) | Code.LOAD_CONST,
                Code.BOP_DIVIDE,
                Code.POP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0);

        Assert.assertTrue(new AggressiveDeadCodeEliminationPass().run(graph));

        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BRANCH));
        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BINARY));
    }

    private static SsaValue lastInstruction(SsaBlock block) {
        return block.instructions().get(block.instructions().size() - 1);
    }

    private static void assertNoInstructionKind(SsaGraph graph, SsaKind kind) {
        Assert.assertEquals(0, countInstructionKind(graph, kind));
    }

    private static int countInstructionKind(SsaGraph graph, SsaKind kind) {
        int count = 0;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (value.kind() == kind) {
                    count++;
                }
            }
        }
        return count;
    }
}
