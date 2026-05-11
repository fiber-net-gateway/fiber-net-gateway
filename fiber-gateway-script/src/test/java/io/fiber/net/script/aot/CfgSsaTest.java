package io.fiber.net.script.aot;

import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

public class CfgSsaTest {

    @Test
    public void shouldUseCatchErrorForCatchVariable() {
        Cfg cfg = build("try { throw 'x'; } catch (e) { return e.message; }");

        Assert.assertTrue(containsInstruction(cfg, CatchError.class));
    }

    @Test
    public void shouldInferCatchErrorTypeFromSingleExplicitThrow() {
        Cfg cfg = build("try { throw 'x'; } catch (e) { return e; }");

        Assert.assertEquals(SsaValue.Type.STRING, findCatchError(cfg).getResultType());
    }

    @Test
    public void shouldUseExceptionTypeForRuntimeThrowOnly() {
        Cfg cfg = build("try { 1 * 0; } catch (e) { return e; }");

        Assert.assertEquals(SsaValue.Type.EXCEPTION, findCatchError(cfg).getResultType());
    }

    @Test
    public void shouldUseUnknownTypeForMixedExplicitAndRuntimeThrow() {
        Cfg cfg = build("try { if ($.x) { throw 'x'; } 1 * 0; } catch (e) { return e; }");

        Assert.assertEquals(SsaValue.Type.Unknown, findCatchError(cfg).getResultType());
    }

    @Test
    public void shouldAvoidPhiWhenSinglePredecessorProvidesValue() {
        Cfg cfg = build("let a = 1 + 2; return a;");

        Assert.assertEquals(0, countPhi(cfg));
    }

    @Test
    public void shouldCreatePhiForDifferentIncomingLocals() {
        Cfg cfg = build("let a = 1; if ($.x) { a = 2; } else { a = 3; } return a;");

        Assert.assertTrue(countPhi(cfg) > 0);
    }

    @Test
    public void shouldRemoveTrivialPhiForSameIncomingLocal() {
        Cfg cfg = build("let a = 1; if ($.x) { } return a;");

        Assert.assertEquals(0, countPhi(cfg));
    }

    private static Cfg build(String script) {
        Compiled compiled = CompilerNodeVisitor.compileFromScript(script, StdLibrary.getDefInstance());
        return new Cfg.Builder(compiled).build();
    }

    private static boolean containsInstruction(Cfg cfg, Class<?> type) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (type.isInstance(instruction)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CatchError findCatchError(Cfg cfg) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof CatchError) {
                    return (CatchError) instruction;
                }
            }
        }
        throw new AssertionError("missing CatchError");
    }

    private static int countPhi(Cfg cfg) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            count += block.getPhiValues().size();
        }
        return count;
    }
}
