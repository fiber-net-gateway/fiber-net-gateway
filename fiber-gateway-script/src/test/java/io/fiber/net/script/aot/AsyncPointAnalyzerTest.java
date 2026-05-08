package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class AsyncPointAnalyzerTest {
    @Test
    public void shouldMarkTemporaryValueLiveAcrossAsyncAsField() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                Code.LOAD_ROOT,
                (0 << 8) | Code.PROP_GET,
                (1 << 8) | Code.CALL_ASYNC_CONST,
                Code.BOP_PLUS,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0);
        SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
        AsyncPlan asyncPlan = new AsyncPointAnalyzer().analyze(graph, liveness);

        SsaValue propGet = firstInstruction(graph, SsaKind.PROP_GET);
        SsaValue async = firstInstruction(graph, SsaKind.ASYNC_CONST_CALL);

        Assert.assertTrue(asyncPlan.isAsyncPoint(async));
        Assert.assertTrue(asyncPlan.fieldValues().contains(propGet));
        Assert.assertTrue(asyncPlan.point(async).liveAcross().contains(propGet));
    }

    @Test
    public void shouldMarkVariableValueLiveAcrossAsyncAsField() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (1 << 8) | Code.CALL_ASYNC_CONST,
                Code.POP,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1);
        SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
        AsyncPlan asyncPlan = new AsyncPointAnalyzer().analyze(graph, liveness);

        SsaValue store = firstInstruction(graph, SsaKind.STORE_VAR);

        Assert.assertTrue(asyncPlan.fieldValues().contains(store));
    }

    @Test
    public void shouldNotCreateFieldForUnusedAsyncResultOnly() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.CALL_ASYNC_CONST,
                Code.POP,
                (1 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0);
        SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
        AsyncPlan asyncPlan = new AsyncPointAnalyzer().analyze(graph, liveness);

        Assert.assertTrue(asyncPlan.fieldValues().isEmpty());
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
