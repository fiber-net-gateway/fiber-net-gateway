package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class SsaBuilderTest {

    @Test
    public void shouldCreateVarPhiAtIfJoin() {
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

        Assert.assertNotNull(join);
        Assert.assertEquals(1, join.phis().size());
        SsaValue phi = join.phis().get(0);
        Assert.assertEquals(SsaKind.PHI, phi.kind());
        Assert.assertEquals(0, phi.index());
        Assert.assertEquals(2, phi.operands().size());
        Assert.assertSame(phi, join.inVars().get(0));
    }

    @Test
    public void shouldCreateStackPhiAtIfExpressionJoin() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (4 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP,
                (3 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        SsaGraph graph = new SsaBuilder().build(cfg, 0);
        SsaBlock join = graph.blockFor(cfg.blockAtPc(5));

        Assert.assertNotNull(join);
        Assert.assertEquals(1, join.phis().size());
        SsaValue phi = join.phis().get(0);
        Assert.assertEquals(SsaKind.PHI, phi.kind());
        Assert.assertEquals(0, phi.index());
        Assert.assertEquals(2, phi.operands().size());
        Assert.assertSame(phi, join.inStack().get(0));
    }

    @Test
    public void shouldCreateLoopHeaderPhi() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                (9 << 8) | Code.JUMP_IF_FALSE,
                (0 << 8) | Code.LOAD_VAR,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_PLUS,
                (0 << 8) | Code.STORE_VAR,
                (2 << 8) | Code.JUMP,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);

        SsaGraph graph = new SsaBuilder().build(cfg, 1);
        SsaBlock header = graph.blockFor(cfg.blockAtPc(2));

        Assert.assertNotNull(header);
        Assert.assertEquals(1, header.phis().size());
        SsaValue phi = header.phis().get(0);
        Assert.assertEquals(SsaKind.PHI, phi.kind());
        Assert.assertEquals(0, phi.index());
        Assert.assertEquals(2, phi.operands().size());
        Assert.assertSame(phi, header.inVars().get(0));
    }

    @Test
    public void shouldCreateCatchExceptionInputAndVarPhi() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (2 << 8) | Code.LOAD_CONST,
                (3 << 8) | Code.LOAD_CONST,
                Code.BOP_DIVIDE,
                Code.POP,
                (9 << 8) | Code.JUMP,
                (0 << 8) | Code.INTO_CATCH,
                (9 << 8) | Code.JUMP,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, new int[]{2, 7, 9});

        SsaGraph graph = new SsaBuilder().build(cfg, 2);
        SsaBlock catchBlock = graph.blockFor(cfg.blockAtPc(7));
        SsaBlock join = graph.blockFor(cfg.blockAtPc(9));

        Assert.assertNotNull(catchBlock.exceptionValue());
        Assert.assertEquals(SsaKind.EXCEPTION, catchBlock.exceptionValue().kind());
        Assert.assertEquals(SsaKind.ERROR_TO_OBJ, catchBlock.instructions().get(0).kind());
        Assert.assertSame(catchBlock.exceptionValue(), catchBlock.instructions().get(0).operands().get(0));

        Assert.assertEquals(1, join.phis().size());
        SsaValue phi = join.phis().get(0);
        Assert.assertEquals(SsaKind.PHI, phi.kind());
        Assert.assertEquals(0, phi.index());
        Assert.assertEquals(2, phi.operands().size());
    }

    @Test
    public void shouldRejectStackHeightMismatchAtJoin() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (1 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP_IF_FALSE,
                (2 << 8) | Code.LOAD_CONST,
                (3 << 8) | Code.LOAD_CONST,
                (6 << 8) | Code.JUMP,
                (4 << 8) | Code.LOAD_CONST,
                Code.END_RETURN
        }, null);

        try {
            new SsaBuilder().build(cfg, 0);
            Assert.fail("expected stack height mismatch");
        } catch (SsaException expected) {
            Assert.assertTrue(expected.getMessage().contains("stack height mismatch"));
        }
    }
}
