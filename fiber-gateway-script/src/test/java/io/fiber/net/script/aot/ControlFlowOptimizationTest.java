package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

public class ControlFlowOptimizationTest {

    @Test
    public void shouldFoldConstantTrueBranch() {
        Cfg cfg = build("if (true) { return 1; } else { return 2; }");

        Assert.assertFalse(containsInstruction(cfg, JumpIfTrue.class));
        Assert.assertFalse(containsInstruction(cfg, JumpIfFalse.class));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldFoldBranchAfterConstantPropagation() {
        Cfg cfg = build("let a = 0; if (1 > 2) { a = 1; } else { a = 2; } return a;");

        Assert.assertEquals(0, countPhi(cfg));
        Assert.assertEquals(IntNode.valueOf(2), returnConst(cfg));
    }

    @Test
    public void shouldRemoveDeadPureExpressions() {
        Cfg cfg = build("let a = 1 + 2; $.unused; return 4;");

        Assert.assertFalse(containsInstruction(cfg, Binary.class));
        Assert.assertFalse(containsInstruction(cfg, PropGet.class));
        Assert.assertEquals(IntNode.valueOf(4), returnConst(cfg));
    }

    @Test
    public void shouldRemoveEmptyDynamicBranch() {
        Cfg cfg = build("let a = 1; if ($.x) {} return a;");

        Assert.assertFalse(containsInstruction(cfg, JumpIfTrue.class));
        Assert.assertFalse(containsInstruction(cfg, JumpIfFalse.class));
        Assert.assertFalse(containsInstruction(cfg, PropGet.class));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldRemoveEmptyDynamicIfElseBranch() {
        Cfg cfg = build("let a = 1; if ($.x) {} else {} return a;");

        Assert.assertFalse(containsInstruction(cfg, JumpIfTrue.class));
        Assert.assertFalse(containsInstruction(cfg, JumpIfFalse.class));
        Assert.assertFalse(containsInstruction(cfg, PropGet.class));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldPruneExceptionEdgeAfterConstantFold() {
        Cfg cfg = build("try { let a = 1 + 2; } catch (e) { return 9; } return 1;");

        Assert.assertFalse(containsInstruction(cfg, CatchError.class));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldPruneExceptionEdgeAfterTypeInferredBinary() {
        Cfg cfg = build("try { let a = 0; if ($.x) { a = 2; } else { a = 3; } a * 4; } catch (e) { return 9; } return 1;");

        Assert.assertFalse(containsInstruction(cfg, CatchError.class));
        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
    }

    @Test
    public void shouldKeepExceptionEdgeForInvalidBinary() {
        Cfg cfg = build("try { 'x' * $.y; } catch (e) { return 9; } return 1;");

        Assert.assertTrue(containsInstruction(cfg, CatchError.class));
        Assert.assertTrue(containsInstruction(cfg, Binary.class));
    }

    @Test
    public void shouldSimplifyAlgebraWithPropagatedNumberType() {
        Cfg cfg = build("let a = 0; if ($.x) { a = 2; } else { a = 3; } return a + 0;");

        Assert.assertFalse(containsInstruction(cfg, Binary.class, Binary.Op.PLUS));
    }

    @Test
    public void shouldEliminateLocalCommonSubexpressions() {
        Cfg cfg = build("let x = $.x; return (x == 1) == (x == 1);");

        Assert.assertEquals(2, countInstruction(cfg, Binary.class));
    }

    @Test
    public void shouldKeepDeadExpressionThatMayThrow() {
        Cfg cfg = build("let a = 'x' * true; return 1;");

        Assert.assertTrue(containsInstruction(cfg, Binary.class));
    }

    private static Cfg build(String script) {
        Compiled compiled = CompilerNodeVisitor.compileFromScript(script, StdLibrary.getDefInstance());
        return new Cfg.Builder(compiled).build();
    }

    private static ValueNode returnConst(Cfg cfg) {
        Expr assign = returnValue(cfg).getAssign();
        Assert.assertTrue(assign instanceof LoadConst);
        return ((LoadConst) assign).getValueNode();
    }

    private static SsaValue returnValue(Cfg cfg) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Ret) {
                    return ((Ret) instruction).getValue();
                }
            }
        }
        throw new AssertionError("missing return");
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

    private static boolean containsInstruction(Cfg cfg, Class<?> type, Binary.Op op) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (type.isInstance(instruction) && ((Binary) instruction).getOp() == op) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countInstruction(Cfg cfg, Class<?> type) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (type.isInstance(instruction)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countPhi(Cfg cfg) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            count += block.getPhiValues().size();
        }
        return count;
    }
}
