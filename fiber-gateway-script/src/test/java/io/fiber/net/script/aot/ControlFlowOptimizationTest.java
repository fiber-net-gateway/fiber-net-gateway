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
    public void shouldRemoveEmptyBlocksAfterOptimization() {
        Cfg cfg = build("let a = 1; if ($.x) {} else {} return a;");

        Assert.assertEquals(0, countEmptyNonEntryBlocks(cfg));
    }

    @Test
    public void shouldRewriteSuccessorPhiWhenPruningEmptyPhiBlock() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        cfg.addBlock(2);
        cfg.addBlock(3);
        Block p1 = cfg.mustGetBlock(0);
        Block p2 = cfg.mustGetBlock(1);
        Block empty = cfg.mustGetBlock(2);
        Block target = cfg.mustGetBlock(3);
        cfg.setEntryBlock(p1);

        LoadConst one = new LoadConst(p1, 0, IntNode.valueOf(1));
        LoadConst two = new LoadConst(p2, 1, IntNode.valueOf(2));
        Phi emptyPhi = empty.newPhi();
        emptyPhi.addCase(p1, one.getResult());
        emptyPhi.addCase(p2, two.getResult());
        empty.addPhi(emptyPhi);
        Phi targetPhi = target.newPhi();
        targetPhi.addCase(empty, emptyPhi.getResult());
        target.addPhi(targetPhi);
        cfg.addEdge(Edge.Type.FALLTHROUGH, p1, empty);
        cfg.addEdge(Edge.Type.JUMP, p2, empty);
        cfg.addEdge(Edge.Type.FALLTHROUGH, empty, target);

        Assert.assertTrue(new EmptyBlockPruning(cfg).optimize());

        Assert.assertFalse(cfg.getBlocks().contains(empty));
        Assert.assertEquals(2, targetPhi.getCases().size());
        Assert.assertTrue(hasPhiCase(targetPhi, p1, one.getResult()));
        Assert.assertTrue(hasPhiCase(targetPhi, p2, two.getResult()));
    }

    @Test
    public void shouldMoveLivePhiWhenPruningEmptyBlock() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        cfg.addBlock(2);
        cfg.addBlock(3);
        Block p1 = cfg.mustGetBlock(0);
        Block p2 = cfg.mustGetBlock(1);
        Block empty = cfg.mustGetBlock(2);
        Block target = cfg.mustGetBlock(3);
        cfg.setEntryBlock(p1);

        LoadConst one = new LoadConst(p1, 0, IntNode.valueOf(1));
        LoadConst two = new LoadConst(p2, 1, IntNode.valueOf(2));
        Phi emptyPhi = empty.newPhi();
        emptyPhi.addCase(p1, one.getResult());
        emptyPhi.addCase(p2, two.getResult());
        empty.addPhi(emptyPhi);
        Ret ret = new Ret(target, 3, emptyPhi.getResult());
        target.getInstructions().add(ret);
        cfg.addEdge(Edge.Type.FALLTHROUGH, p1, empty);
        cfg.addEdge(Edge.Type.JUMP, p2, empty);
        cfg.addEdge(Edge.Type.FALLTHROUGH, empty, target);

        Assert.assertTrue(new EmptyBlockPruning(cfg).optimize());

        Assert.assertFalse(cfg.getBlocks().contains(empty));
        Assert.assertTrue(ret.getValue().getAssign() instanceof Phi);
        Phi moved = (Phi) ret.getValue().getAssign();
        Assert.assertSame(target, moved.getBelongTo());
        Assert.assertTrue(hasPhiCase(moved, p1, one.getResult()));
        Assert.assertTrue(hasPhiCase(moved, p2, two.getResult()));
    }

    @Test
    public void shouldMoveEmptyEntryToOnlySuccessor() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        Block entry = cfg.mustGetBlock(0);
        Block target = cfg.mustGetBlock(1);
        cfg.setEntryBlock(entry);
        cfg.addEdge(Edge.Type.FALLTHROUGH, entry, target);

        Assert.assertTrue(new EmptyBlockPruning(cfg).optimize());

        Assert.assertSame(target, cfg.getEntryBlock());
        Assert.assertFalse(cfg.getBlocks().contains(entry));
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

    private static int countEmptyNonEntryBlocks(Cfg cfg) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            if (block != cfg.getEntryBlock()
                    && block.getInstructions().isEmpty()
                    && block.getPhiValues().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasPhiCase(Phi phi, Block from, SsaValue value) {
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.from == from && aCase.value == value) {
                return true;
            }
        }
        return false;
    }

    private static int countPhi(Cfg cfg) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            count += block.getPhiValues().size();
        }
        return count;
    }
}
