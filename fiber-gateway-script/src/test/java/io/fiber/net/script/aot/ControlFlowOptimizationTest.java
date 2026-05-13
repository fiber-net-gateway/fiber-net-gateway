package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
    public void shouldPruneFallthroughAfterAlwaysThrowingBinary() {
        Cfg cfg = build("let a = 0; try { 'x' * true; a = 1; } catch (e) { a = 2; } return a;");

        Assert.assertTrue(containsInstruction(cfg, Binary.class));
        Assert.assertEquals(IntNode.valueOf(2), returnConst(cfg));
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
    public void shouldMergeFoldedBranchToSingleReturnBlock() {
        Cfg cfg = build("let x = 1; if(x + 3 > 4) {return 3;} else {return x+5;}");

        Assert.assertEquals(1, countBlocks(cfg));
        Assert.assertEquals(IntNode.valueOf(6), returnConst(cfg));
    }

    @Test
    public void shouldBypassJumpOnlyBlock() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        cfg.addBlock(2);
        Block predecessor = cfg.mustGetBlock(0);
        Block jumpOnly = cfg.mustGetBlock(1);
        Block target = cfg.mustGetBlock(2);
        cfg.setEntryBlock(predecessor);
        Jump predecessorJump = new Jump(predecessor, 0, jumpOnly);
        predecessor.getInstructions().add(predecessorJump);
        jumpOnly.getInstructions().add(new Jump(jumpOnly, 1, target));
        cfg.addEdge(Edge.Type.JUMP, predecessor, jumpOnly);
        cfg.addEdge(Edge.Type.JUMP, jumpOnly, target);

        Assert.assertTrue(new JumpOnlyBlockPruning(cfg).optimize());

        Assert.assertFalse(cfg.getBlocks().contains(jumpOnly));
        Assert.assertSame(target, ((Jump) predecessor.getInstructions().get(0)).getTarget());
        Assert.assertEquals(1, predecessor.getSuccessors().size());
        Assert.assertSame(target, predecessor.getSuccessors().get(0).successor);
    }

    @Test
    public void shouldMergeLinearBlocksAndMoveInstructionOwner() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        Block entry = cfg.mustGetBlock(0);
        Block target = cfg.mustGetBlock(1);
        cfg.setEntryBlock(entry);
        LoadConst value = new LoadConst(entry, 0, IntNode.valueOf(6));
        Ret ret = new Ret(target, 1, value.getResult());
        entry.getInstructions().add(value);
        target.getInstructions().add(ret);
        cfg.addEdge(Edge.Type.FALLTHROUGH, entry, target);

        Assert.assertTrue(new LinearBlockMerging(cfg).optimize());

        Assert.assertFalse(cfg.getBlocks().contains(target));
        Assert.assertEquals(2, entry.getInstructions().size());
        Assert.assertSame(entry, ret.getBelongTo());
        Assert.assertSame(ret, entry.getInstructions().get(1));
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
    public void shouldEliminateDominatedCommonBinaryExpression() {
        Cfg cfg = build("let x = 0; if ($.c) { x = 1; } else { x = 2; } let a = x + 1; if ($.d) { includes(\"abc\", \"a\"); } return a == (x + 1);");

        Assert.assertEquals(1, countBinary(cfg, Binary.Op.PLUS));
    }

    @Test
    public void shouldEliminateDominatedPropertyGet() {
        Cfg cfg = build("let u = $.user; let x = u.id; if ($.c) { includes(\"abc\", \"a\"); } return x == u.id;");

        Assert.assertEquals(1, countPropGet(cfg, "id"));
    }

    @Test
    public void shouldKeepPropertyGetAcrossPropertySetBarrier() {
        Cfg cfg = build("let u = $.user; let x = u.id; if ($.c) { includes(\"abc\", \"a\"); } u.id = 2; return x == u.id;");

        Assert.assertEquals(2, countPropGet(cfg, "id"));
    }

    @Test
    public void shouldKeepPropertyGetAcrossCallBarrier() {
        Cfg cfg = build("let u = $.user; let x = u.id; if ($.c) { includes(\"abc\", \"a\"); } includes(\"abc\", \"b\"); return x == u.id;");

        Assert.assertEquals(2, countPropGet(cfg, "id"));
    }

    @Test
    public void shouldKeepArithmeticExpressionAcrossCallBarrier() {
        Cfg cfg = build("let x = 0; if ($.c) { x = 1; } else { x = 2; } let a = x + 1; if ($.d) { includes(\"abc\", \"a\"); } includes(\"abc\", \"b\"); return a == (x + 1);");

        Assert.assertEquals(1, countBinary(cfg, Binary.Op.PLUS));
    }

    @Test
    public void shouldKeepDeadExpressionThatMayThrow() {
        Cfg cfg = build("let a = 'x' * true; return 1;");

        Assert.assertTrue(containsInstruction(cfg, Binary.class));
    }

    @Test
    public void shouldHoistLoopInvariantBinaryExpression() {
        Cfg cfg = build("let x = 0; if ($.c) { x = 1; } else { x = 2; } let s = 0; for(let _, v of [1,2,3]) { s = s + (x + 1); } return s;");
        Set<Block> loopBlocks = firstLoop(cfg);

        Assert.assertEquals(1, countBinary(loopBlocks, Binary.Op.PLUS));
        Assert.assertEquals(1, countBinaryOutside(cfg, loopBlocks, Binary.Op.PLUS));
    }

    @Test
    public void shouldNotHoistLoopExpressionThatMayThrow() {
        Cfg cfg = build("let x = $.x; let s = 0; for(let _, v of [1,2,3]) { s = s + (x * 2); } return s;");
        Set<Block> loopBlocks = firstLoop(cfg);

        Assert.assertEquals(1, countBinary(loopBlocks, Binary.Op.MULTIPLY));
        Assert.assertEquals(0, countBinaryOutside(cfg, loopBlocks, Binary.Op.MULTIPLY));
    }

    @Test
    public void shouldKeepPropertyGetInsideLoop() {
        Cfg cfg = build("let s = 0; for(let _, v of [1,2,3]) { if ($.config.id) { s = s + 1; } } return s;");
        Set<Block> loopBlocks = firstLoop(cfg);

        Assert.assertEquals(1, countPropGet(loopBlocks, "id"));
    }

    @Test
    public void shouldKeepIteratorOperationsInsideLoop() {
        Cfg cfg = build("let s = 0; for(let _, v of [1,2,3]) { s = s + v; } return s;");
        Set<Block> loopBlocks = firstLoop(cfg);

        Assert.assertEquals(1, countUnary(loopBlocks, Unary.Op.ITERATE_NEXT));
        Assert.assertEquals(1, countUnary(loopBlocks, Unary.Op.ITERATE_VALUE));
    }

    @Test
    public void shouldReplaceFreshObjectPropertyGet() {
        Cfg cfg = build("let o = {}; o.a = 3; return o.a;");

        Assert.assertEquals(IntNode.valueOf(3), returnConst(cfg));
        Assert.assertFalse(containsInstruction(cfg, NewObj.class));
        Assert.assertFalse(containsInstruction(cfg, PropSet.class));
        Assert.assertFalse(containsInstruction(cfg, PropGet.class));
    }

    @Test
    public void shouldReplaceObjectLiteralPropertyGet() {
        Cfg cfg = build("let o = {a: 1, b: 2}; return o.a;");

        Assert.assertEquals(IntNode.valueOf(1), returnConst(cfg));
        Assert.assertFalse(containsInstruction(cfg, NewObj.class));
        Assert.assertFalse(containsInstruction(cfg, PropGet.class));
    }

    @Test
    public void shouldPreserveFreshObjectPropertyAssignmentResult() {
        Cfg cfg = build("let o = {}; return o.a = 7;");

        Assert.assertEquals(IntNode.valueOf(7), returnConst(cfg));
        Assert.assertFalse(containsInstruction(cfg, NewObj.class));
        Assert.assertFalse(containsInstruction(cfg, PropSet.class));
    }

    @Test
    public void shouldCreateFieldPhiForFreshObjectPropertyAcrossBranches() {
        Cfg cfg = build("let o = {}; if ($.x) { o.a = 1; } else { o.a = 2; } return o.a;");

        Assert.assertFalse(containsInstruction(cfg, NewObj.class));
        Assert.assertFalse(containsInstruction(cfg, PropSet.class));
        Assert.assertEquals(0, countPropGet(cfg, "a"));
        Assert.assertTrue(returnValue(cfg).getAssign() instanceof Phi);
    }

    @Test
    public void shouldKeepEscapingFreshObject() {
        Cfg cfg = build("let o = {}; o.a = 1; return o;");

        Assert.assertTrue(containsInstruction(cfg, NewObj.class));
        Assert.assertTrue(containsInstruction(cfg, PropSet.class));
    }

    @Test
    public void shouldReplaceFreshArrayIndexGet() {
        Cfg cfg = build("let a = [1, 2]; return a[1];");

        Assert.assertEquals(IntNode.valueOf(2), returnConst(cfg));
        Assert.assertFalse(containsInstruction(cfg, NewArr.class));
        Assert.assertFalse(containsInstruction(cfg, PushArr.class));
        Assert.assertFalse(containsInstruction(cfg, IndexGet.class));
    }

    @Test
    public void shouldKeepEscapingFreshArray() {
        Cfg cfg = build("let a = [1, 2]; return a;");

        Assert.assertTrue(containsInstruction(cfg, NewArr.class));
        Assert.assertTrue(containsInstruction(cfg, PushArr.class));
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

    private static int countBinary(Cfg cfg, Binary.Op op) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Binary && ((Binary) instruction).getOp() == op) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countPropGet(Cfg cfg, String key) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof PropGet && key.equals(((PropGet) instruction).getKey())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countPropGet(Set<Block> blocks, String key) {
        int count = 0;
        for (Block block : blocks) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof PropGet && key.equals(((PropGet) instruction).getKey())) {
                    count++;
                }
            }
        }
        return count;
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

    private static int countBinary(Set<Block> blocks, Binary.Op op) {
        int count = 0;
        for (Block block : blocks) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Binary && ((Binary) instruction).getOp() == op) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countBinaryOutside(Cfg cfg, Set<Block> blocks, Binary.Op op) {
        int count = 0;
        for (Block block : cfg.getBlocks()) {
            if (blocks.contains(block)) {
                continue;
            }
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Binary && ((Binary) instruction).getOp() == op) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countUnary(Set<Block> blocks, Unary.Op op) {
        int count = 0;
        for (Block block : blocks) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Unary && ((Unary) instruction).getOp() == op) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Set<Block> firstLoop(Cfg cfg) {
        Dominators dominators = Dominators.compute(cfg);
        for (Block block : dominators.reversePostOrder) {
            for (Edge edge : block.getSuccessors()) {
                if (edge.type != Edge.Type.THROW && dominators.dominates(edge.successor, block)) {
                    return naturalLoop(edge.successor, block);
                }
            }
        }
        throw new AssertionError("missing loop");
    }

    private static Set<Block> naturalLoop(Block header, Block latch) {
        Set<Block> loopBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        ArrayDeque<Block> queue = new ArrayDeque<>();
        loopBlocks.add(header);
        loopBlocks.add(latch);
        queue.add(latch);
        while (!queue.isEmpty()) {
            Block block = queue.poll();
            for (Edge edge : block.getPredecessors()) {
                if (edge.type == Edge.Type.THROW) {
                    continue;
                }
                Block predecessor = edge.predecessor;
                if (loopBlocks.add(predecessor) && predecessor != header) {
                    queue.add(predecessor);
                }
            }
        }
        return loopBlocks;
    }

    private static int countBlocks(Cfg cfg) {
        return cfg.getBlocks().size();
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
