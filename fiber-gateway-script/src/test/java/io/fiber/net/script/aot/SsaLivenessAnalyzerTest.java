package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class SsaLivenessAnalyzerTest {
    @Test
    public void shouldCreatePhiEdgeMovesAtVarJoin() {
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
        SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
        SsaBlock join = graph.blockFor(cfg.blockAtPc(9));
        SsaValue phi = join.phis().get(0);

        int movesToPhi = 0;
        for (Edge edge : join.basicBlock().predecessors()) {
            for (SsaLiveness.PhiMove move : liveness.edgeMoves(edge)) {
                if (move.to() == phi) {
                    movesToPhi++;
                    Assert.assertNotSame(phi, move.from());
                }
            }
        }
        Assert.assertEquals(2, movesToPhi);
        Assert.assertFalse(liveness.liveIn(join).contains(phi));
    }

    @Test
    public void shouldReportValueLiveAfterAsyncWhenUsedByContinuation() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.LOAD_ROOT,
                (0 << 8) | Code.PROP_GET,
                (1 << 8) | Code.CALL_ASYNC_CONST,
                Code.BOP_PLUS,
                Code.END_RETURN
        }, null);

        SsaGraph graph = new SsaBuilder().build(cfg, 0);
        SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
        SsaValue propGet = firstInstruction(graph, SsaKind.PROP_GET);
        SsaValue async = firstInstruction(graph, SsaKind.ASYNC_CONST_CALL);

        Assert.assertTrue(liveness.liveAfter(async).contains(propGet));
    }

    private static SsaValue firstInstruction(SsaGraph graph, SsaKind kind) {
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (value.kind() == kind) {
                    return value;
                }
            }
        }
        throw new AssertionError("missing instruction kind " + kind);
    }
}
