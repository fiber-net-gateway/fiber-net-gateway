package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.ResolvedFunc;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Unaries;

import java.util.ArrayList;

public final class ConstantPropagationPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        boolean changed = false;
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : new ArrayList<>(block.instructions())) {
                JsonNode constant = fold(graph, value);
                if (constant == null) {
                    continue;
                }
                SsaValue replacement = graph.createConstBefore(block, value, constant);
                changed |= SsaEditor.replaceAllUses(graph, value, replacement);
                changed |= SsaEditor.removeValue(value);
            }
        }
        if (changed) {
            new DeadCodeEliminationPass().run(graph);
        }
        return changed;
    }

    private static JsonNode fold(SsaGraph graph, SsaValue value) {
        Bytecode bytecode = value.bytecode();
        if (bytecode == null) {
            return null;
        }
        try {
            switch (value.kind()) {
                case BINARY:
                    return foldBinary(bytecode.op(), constantOperand(graph, value, 0), constantOperand(graph, value, 1));
                case UNARY:
                    return foldUnary(bytecode.op(), constantOperand(graph, value, 0));
                case CALL:
                    return foldConstExprCall(graph, value);
                default:
                    return null;
            }
        } catch (ScriptExecException | RuntimeException ignored) {
            return null;
        }
    }

    private static JsonNode constantOperand(SsaGraph graph, SsaValue value, int index) {
        if (value.operands().size() <= index) {
            return null;
        }
        SsaValue operand = value.operands().get(index);
        if (operand.kind() != SsaKind.CONST) {
            return null;
        }
        Object constant = graph.constant(operand.index());
        return constant instanceof JsonNode ? (JsonNode) constant : null;
    }

    private static JsonNode foldBinary(Op op, JsonNode left, JsonNode right) throws ScriptExecException {
        if (left == null || right == null) {
            return null;
        }
        switch (op) {
            case BOP_PLUS:
                return Binaries.plus(left, right);
            case BOP_MINUS:
                return Binaries.minus(left, right);
            case BOP_MULTIPLY:
                return Binaries.multiply(left, right);
            case BOP_MATCH:
                return Binaries.matches(left, right);
            case BOP_LT:
                return Binaries.lt(left, right);
            case BOP_LTE:
                return Binaries.lte(left, right);
            case BOP_GT:
                return Binaries.gt(left, right);
            case BOP_GTE:
                return Binaries.gte(left, right);
            case BOP_EQ:
                return Binaries.eq(left, right);
            case BOP_SEQ:
                return Binaries.seq(left, right);
            case BOP_NE:
                return Binaries.ne(left, right);
            case BOP_SNE:
                return Binaries.sne(left, right);
            case BOP_IN:
                return Binaries.in(left, right);
            default:
                return null;
        }
    }

    private static JsonNode foldUnary(Op op, JsonNode value) throws ScriptExecException {
        if (value == null) {
            return null;
        }
        switch (op) {
            case UNARY_PLUS:
                return Unaries.plus(value);
            case UNARY_MINUS:
                return Unaries.minus(value);
            case UNARY_NEG:
                return Unaries.neg(value);
            case UNARY_TYPEOF:
                return Unaries.typeof(value);
            default:
                return null;
        }
    }

    private static JsonNode foldConstExprCall(SsaGraph graph, SsaValue value) throws ScriptExecException {
        Object operand = graph.constant(value.index());
        if (!(operand instanceof ResolvedFunc)) {
            return null;
        }
        ResolvedFunc func = (ResolvedFunc) operand;
        if (func.isAsync() || !func.getSignature().isConstExpr()) {
            return null;
        }
        Library.Function function = func.getFunction();
        if (function == null || !function.isConstExpr()) {
            return null;
        }
        JsonNode[] args = new JsonNode[value.operands().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = constantOperand(graph, value, i);
            if (args[i] == null) {
                return null;
            }
        }
        JsonNode result = function.call(null, new ConstArguments(args));
        return result == null ? NullNode.getInstance() : result.deepCopy();
    }

    private static final class ConstArguments implements Library.Arguments {
        private final JsonNode[] args;

        private ConstArguments(JsonNode[] args) {
            this.args = args;
        }

        @Override
        public JsonNode getArgVal(int idx) {
            return args[idx];
        }

        @Override
        public int getArgCnt() {
            return args.length;
        }
    }
}
