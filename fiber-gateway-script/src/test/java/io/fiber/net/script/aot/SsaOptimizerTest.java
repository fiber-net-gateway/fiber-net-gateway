package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.FunctionSignature;
import io.fiber.net.script.Library;
import io.fiber.net.script.ResolvedFunc;
import io.fiber.net.script.run.Code;
import org.junit.Assert;
import org.junit.Test;

public class SsaOptimizerTest {

    @Test
    public void shouldPropagateCopiesAndEliminateDeadInstructions() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1);

        Assert.assertTrue(SsaOptimizer.conservative().optimize(graph));

        SsaValue ret = lastInstruction(graph.entryBlock());
        Assert.assertEquals(SsaKind.RETURN, ret.kind());
        Assert.assertEquals(SsaKind.CONST, ret.operands().get(0).kind());
        assertNoInstructionKind(graph, SsaKind.LOAD_VAR);
        assertNoInstructionKind(graph, SsaKind.STORE_VAR);
    }

    @Test
    public void shouldFoldConstantBinaryExpression() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_PLUS,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2)
        });

        Assert.assertTrue(new ConstantPropagationPass().run(graph));

        SsaValue ret = lastInstruction(graph.entryBlock());
        SsaValue constant = ret.operands().get(0);
        Assert.assertEquals(SsaKind.CONST, constant.kind());
        Assert.assertEquals(IntNode.valueOf(3), graph.constant(constant.index()));
        assertNoInstructionKind(graph, SsaKind.BINARY);
    }

    @Test
    public void shouldEliminateCommonSubexpressionInBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2)
        });

        Assert.assertTrue(new CommonSubexpressionEliminationPass().run(graph));

        SsaValue ret = lastInstruction(graph.entryBlock());
        SsaValue returned = ret.operands().get(0);
        Assert.assertEquals(SsaKind.BINARY, returned.kind());
        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BINARY));
    }

    @Test
    public void shouldEliminateCommutativeCommonSubexpressionInBlock() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                (0 << 8) | Code.STORE_VAR,
                (1 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2)
        });

        Assert.assertTrue(new CommonSubexpressionEliminationPass().run(graph));

        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BINARY));
    }

    @Test
    public void shouldSimplifyPhiAfterCopiesCollapseInputs() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (1 << 8) | Code.LOAD_CONST,
                (7 << 8) | Code.JUMP_IF_FALSE,
                (0 << 8) | Code.LOAD_VAR,
                (0 << 8) | Code.STORE_VAR,
                (9 << 8) | Code.JUMP,
                (0 << 8) | Code.LOAD_VAR,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                IntNode.valueOf(3),
                IntNode.valueOf(1)
        });

        Assert.assertTrue(new CopyPropagationPass().run(graph));
        Assert.assertTrue(new PhiSimplificationPass().run(graph));

        SsaValue ret = lastInstruction(lastBlock(graph));
        Assert.assertEquals(SsaKind.RETURN, ret.kind());
        Assert.assertEquals(SsaKind.CONST, ret.operands().get(0).kind());
        Assert.assertEquals(0, countPhis(graph));
    }

    @Test
    public void shouldUseValueNumbersForGlobalValueNumbering() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                (1 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 2, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2)
        });

        Assert.assertTrue(new GlobalValueNumberingPass().run(graph));

        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BINARY));
    }

    @Test
    public void shouldEliminateDominatedGlobalCommonSubexpression() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                (0 << 8) | Code.STORE_VAR,
                (2 << 8) | Code.LOAD_CONST,
                (10 << 8) | Code.JUMP_IF_FALSE,
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2),
                IntNode.valueOf(1)
        });

        Assert.assertTrue(new GlobalValueNumberingPass().run(graph));

        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.BINARY));
    }

    @Test
    public void shouldNotEliminateCommonSubexpressionAcrossSiblingBranches() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (2 << 8) | Code.LOAD_CONST,
                (6 << 8) | Code.JUMP_IF_FALSE,
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN,
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_EQ,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2),
                IntNode.valueOf(1)
        });

        Assert.assertFalse(new GlobalValueNumberingPass().run(graph));

        Assert.assertEquals(2, countInstructionKind(graph, SsaKind.BINARY));
    }

    @Test
    public void shouldRunDefaultOptimizer() {
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                Code.BOP_PLUS,
                (0 << 8) | Code.STORE_VAR,
                (0 << 8) | Code.LOAD_VAR,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 1, new Object[]{
                IntNode.valueOf(1),
                IntNode.valueOf(2)
        });

        Assert.assertTrue(SsaOptimizer.defaults().optimize(graph));

        SsaValue ret = lastInstruction(graph.entryBlock());
        SsaValue returned = ret.operands().get(0);
        Assert.assertEquals(SsaKind.CONST, returned.kind());
        Assert.assertEquals(IntNode.valueOf(3), graph.constant(returned.index()));
    }

    @Test
    public void shouldFoldConstExprFunctionCallWithConstantArguments() {
        ResolvedFunc add = ResolvedFunc.sync(
                FunctionSignature.fixed("add", true),
                new Library.Function() {
                    @Override
                    public FunctionSignature signature() {
                        return FunctionSignature.fixed("add", true);
                    }

                    @Override
                    public JsonNode call(ExecutionContext context, Library.Arguments args) {
                        return IntNode.valueOf(args.getArgVal(0).asInt() + args.getArgVal(1).asInt());
                    }
                }
        );
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                (2 << 16) | (2 << 8) | Code.CALL_FUNC,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0, new Object[]{
                IntNode.valueOf(2),
                IntNode.valueOf(3),
                add
        });

        Assert.assertTrue(new ConstantPropagationPass().run(graph));

        SsaValue ret = lastInstruction(graph.entryBlock());
        SsaValue returned = ret.operands().get(0);
        Assert.assertEquals(SsaKind.CONST, returned.kind());
        Assert.assertEquals(IntNode.valueOf(5), graph.constant(returned.index()));
        assertNoInstructionKind(graph, SsaKind.CALL);
    }

    @Test
    public void shouldNotFoldNonConstExprFunctionCall() {
        ResolvedFunc add = ResolvedFunc.sync(
                FunctionSignature.fixed("add", false),
                new Library.Function() {
                    @Override
                    public FunctionSignature signature() {
                        return FunctionSignature.fixed("add", false);
                    }

                    @Override
                    public JsonNode call(ExecutionContext context, Library.Arguments args) {
                        return IntNode.valueOf(args.getArgVal(0).asInt() + args.getArgVal(1).asInt());
                    }
                }
        );
        Cfg cfg = new AotCfgBuilder().build(new int[]{
                (0 << 8) | Code.LOAD_CONST,
                (1 << 8) | Code.LOAD_CONST,
                (2 << 16) | (2 << 8) | Code.CALL_FUNC,
                Code.END_RETURN
        }, null);
        SsaGraph graph = new SsaBuilder().build(cfg, 0, new Object[]{
                IntNode.valueOf(2),
                IntNode.valueOf(3),
                add
        });

        Assert.assertFalse(new ConstantPropagationPass().run(graph));
        Assert.assertEquals(1, countInstructionKind(graph, SsaKind.CALL));
    }

    private static SsaValue lastInstruction(SsaBlock block) {
        return block.instructions().get(block.instructions().size() - 1);
    }

    private static SsaBlock lastBlock(SsaGraph graph) {
        return graph.blocks().get(graph.blocks().size() - 1);
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

    private static int countPhis(SsaGraph graph) {
        int count = 0;
        for (SsaBlock block : graph.blocks()) {
            count += block.phis().size();
        }
        return count;
    }
}
