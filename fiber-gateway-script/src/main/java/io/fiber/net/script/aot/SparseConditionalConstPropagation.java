package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Compares;
import io.fiber.net.script.run.Unaries;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SparseConditionalConstPropagation {

    private final Cfg cfg;
    private final Map<SsaValue, Const> constants = new IdentityHashMap<>();
    private final Set<Block> executableBlocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private final Set<Edge> executableEdges = Collections.newSetFromMap(new IdentityHashMap<Edge, Boolean>());
    private final ArrayDeque<Block> blockQueue = new ArrayDeque<>();
    private final ArrayDeque<Instruction> ssaQueue = new ArrayDeque<>();

    public SparseConditionalConstPropagation(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        analyze();
        return rewrite();
    }

    private void analyze() {
        markExecutable(cfg.getEntryBlock());
        while (!blockQueue.isEmpty() || !ssaQueue.isEmpty()) {
            while (!blockQueue.isEmpty()) {
                visitBlock(blockQueue.poll());
            }
            if (!ssaQueue.isEmpty()) {
                visitInstruction(ssaQueue.poll());
            }
        }
    }

    private void visitBlock(Block block) {
        for (Phi phi : block.getPhiValues()) {
            updateValue(phi);
        }
        for (Instruction instruction : block.getInstructions()) {
            visitInstruction(instruction);
        }
        visitTerminator(block);
    }

    private void visitInstruction(Instruction instruction) {
        if (!executableBlocks.contains(instruction.getBelongTo())) {
            return;
        }
        if (instruction instanceof Expr) {
            updateValue((Expr) instruction);
        }
        if (instruction instanceof JumpIfTrue || instruction instanceof JumpIfFalse) {
            visitTerminator(instruction.getBelongTo());
        }
    }

    private void updateValue(Expr expr) {
        SsaValue result = expr.getResult();
        Const oldConst = get(result);
        Const newConst = evaluate(expr);
        if (oldConst.equals(newConst)) {
            return;
        }
        constants.put(result, newConst);
        ssaQueue.addAll(result.getUsed());
    }

    private void visitTerminator(Block block) {
        if (!executableBlocks.contains(block)) {
            return;
        }
        if (block.getInstructions().isEmpty()) {
            markNormalSuccessors(block);
            return;
        }
        Instruction terminal = block.getInstructions().get(block.getInstructions().size() - 1);
        if (terminal instanceof Jump) {
            markSuccessor(block, ((Jump) terminal).getTarget(), Edge.Type.JUMP);
            return;
        }
        if (terminal instanceof JumpIfTrue) {
            markConditionalSuccessors(block, ((JumpIfTrue) terminal).getCond(), true);
            return;
        }
        if (terminal instanceof JumpIfFalse) {
            markConditionalSuccessors(block, ((JumpIfFalse) terminal).getCond(), false);
            return;
        }
        if (terminal instanceof Ret || terminal instanceof RetV) {
            return;
        }

        switch (terminal.canThrow()) {
            case NOT:
                markNormalSuccessors(block);
                break;
            case MAYBE:
                markAllSuccessors(block);
                break;
            case ALWAYS:
                if (terminal instanceof Throw) {
                    markThrowSuccessors(block);
                } else {
                    markAllSuccessors(block);
                }
                break;
            default:
                throw new IllegalStateException("[bug]unknown throw kind");
        }
    }

    private void markConditionalSuccessors(Block block, SsaValue cond, boolean jumpOnTrue) {
        Const constant = get(cond);
        if (constant.isConst()) {
            boolean logic = Compares.logic(constant.value);
            if (logic == jumpOnTrue) {
                markJumpSuccessor(block);
            } else {
                markFallthroughSuccessor(block);
            }
            return;
        }
        if (constant.isOverdefined()) {
            markJumpSuccessor(block);
            markFallthroughSuccessor(block);
        }
    }

    private void markExecutable(Block block) {
        if (executableBlocks.add(block)) {
            blockQueue.add(block);
        }
    }

    private void markEdge(Edge edge) {
        if (!executableEdges.add(edge)) {
            return;
        }
        boolean alreadyExecutable = executableBlocks.contains(edge.successor);
        markExecutable(edge.successor);
        if (alreadyExecutable) {
            ssaQueue.addAll(edge.successor.getPhiValues());
        }
    }

    private void markSuccessor(Block block, Block successor, Edge.Type type) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.successor == successor && edge.type == type) {
                markEdge(edge);
                return;
            }
        }
    }

    private void markJumpSuccessor(Block block) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.JUMP) {
                markEdge(edge);
                return;
            }
        }
    }

    private void markFallthroughSuccessor(Block block) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.FALLTHROUGH) {
                markEdge(edge);
                return;
            }
        }
    }

    private void markNormalSuccessors(Block block) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.type != Edge.Type.THROW) {
                markEdge(edge);
            }
        }
    }

    private void markThrowSuccessors(Block block) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.type == Edge.Type.THROW) {
                markEdge(edge);
            }
        }
    }

    private void markAllSuccessors(Block block) {
        for (Edge edge : block.getSuccessors()) {
            markEdge(edge);
        }
    }

    private boolean rewrite() {
        boolean changed = false;
        changed |= rewriteConstants();
        changed |= rewriteBranches();
        changed |= removeUnreachableBlocks();
        return changed;
    }

    private boolean rewriteConstants() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            if (!executableBlocks.contains(block)) {
                continue;
            }
            for (int i = 0; i < block.getPhiValues().size(); i++) {
                Phi phi = block.getPhiValues().get(i);
                Const constant = get(phi.getResult());
                if (!constant.isConst()) {
                    continue;
                }
                SsaValue replacement = findConstReplacement(phi, constant.value);
                if (replacement == null) {
                    continue;
                }
                cfg.replaceValue(phi.getResult(), replacement);
                block.removePhi(phi);
                changed = true;
                i--;
            }

            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (!(instruction instanceof Expr) || instruction instanceof LoadConst) {
                    continue;
                }
                Expr expr = (Expr) instruction;
                Const constant = get(expr.getResult());
                if (!constant.isConst()) {
                    continue;
                }
                ConstantValues.replaceWithConst(block, expr, constant.value);
                changed = true;
            }
        }
        return changed;
    }

    private boolean rewriteBranches() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            if (!executableBlocks.contains(block)) {
                continue;
            }
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                Boolean jumpTaken = constantJumpDecision(instruction);
                if (jumpTaken == null) {
                    continue;
                }
                Edge kept = findKeptEdge(block, instruction, jumpTaken);
                if (kept == null) {
                    continue;
                }
                for (Edge edge : block.getSuccessors().toArray(new Edge[0])) {
                    if (edge != kept) {
                        cfg.removeEdge(edge);
                    }
                }
                instruction.dropOperands();
                if (jumpTaken) {
                    block.replaceInstruction(instruction, new Jump(block, instruction.getPc(), kept.successor));
                } else {
                    block.removeInstruction(instruction);
                }
                changed = true;
            }
        }
        return changed;
    }

    private boolean removeUnreachableBlocks() {
        boolean changed = false;
        for (Block block : cfg.getBlocks().toArray(new Block[0])) {
            if (executableBlocks.contains(block)) {
                continue;
            }
            cfg.removeBlock(block);
            changed = true;
        }
        return changed;
    }

    private Boolean constantJumpDecision(Instruction instruction) {
        if (instruction instanceof JumpIfTrue) {
            Const constant = get(((JumpIfTrue) instruction).getCond());
            return constant.isConst() ? Compares.logic(constant.value) : null;
        }
        if (instruction instanceof JumpIfFalse) {
            Const constant = get(((JumpIfFalse) instruction).getCond());
            return constant.isConst() ? !Compares.logic(constant.value) : null;
        }
        return null;
    }

    private SsaValue findConstReplacement(Phi phi, ValueNode value) {
        for (Phi.Case aCase : phi.getCases()) {
            if (!isExecutableIncoming(phi.getBelongTo(), aCase.from) || aCase.value == phi.getResult()) {
                continue;
            }
            Const constant = get(aCase.value);
            if (constant.isConst() && Objects.equals(constant.value, value)) {
                return aCase.value;
            }
        }
        return null;
    }

    private Edge findKeptEdge(Block block, Instruction instruction, boolean jumpTaken) {
        Block target = instruction instanceof JumpIfTrue
                ? ((JumpIfTrue) instruction).getTarget()
                : ((JumpIfFalse) instruction).getTarget();
        for (Edge edge : block.getSuccessors()) {
            if (jumpTaken) {
                if (edge.type == Edge.Type.JUMP && edge.successor == target) {
                    return edge;
                }
            } else if (edge.type == Edge.Type.FALLTHROUGH) {
                return edge;
            }
        }
        return null;
    }

    private Const evaluate(Expr expr) {
        if (expr instanceof LoadConst) {
            return Const.of(((LoadConst) expr).getValueNode());
        }
        if (expr instanceof Phi) {
            return evaluatePhi((Phi) expr);
        }
        if (expr instanceof Unary) {
            return evaluateUnary((Unary) expr);
        }
        if (expr instanceof Binary) {
            return evaluateBinary((Binary) expr);
        }
        if (expr instanceof PropGet) {
            return evaluatePropGet((PropGet) expr);
        }
        if (expr instanceof IndexGet) {
            return evaluateIndexGet((IndexGet) expr);
        }
        return Const.OVERDEFINED;
    }

    private Const evaluatePhi(Phi phi) {
        Const merged = Const.UNDEF;
        for (Phi.Case aCase : phi.getCases()) {
            if (!isExecutableIncoming(phi.getBelongTo(), aCase.from)) {
                continue;
            }
            merged = merge(merged, get(aCase.value));
            if (merged.isOverdefined()) {
                return merged;
            }
        }
        return merged;
    }

    private boolean isExecutableIncoming(Block block, Block predecessor) {
        if (block == cfg.getEntryBlock() && predecessor == null) {
            return true;
        }
        for (Edge edge : predecessor.getSuccessors()) {
            if (edge.successor == block && executableEdges.contains(edge)) {
                return true;
            }
        }
        return false;
    }

    private Const evaluateUnary(Unary unary) {
        Const material = get(unary.getMaterial());
        if (!material.isConst()) {
            return material;
        }
        try {
            switch (unary.getOp()) {
                case PLUS:
                    return constResult(Unaries.plus(material.value));
                case MINUS:
                    return constResult(Unaries.minus(material.value));
                case NEG:
                    return constResult(Unaries.neg(material.value));
                case TYPEOF:
                    return constResult(Unaries.typeof(material.value));
                default:
                    return Const.OVERDEFINED;
            }
        } catch (Exception e) {
            return Const.OVERDEFINED;
        }
    }

    private Const evaluateBinary(Binary binary) {
        Const left = get(binary.getLeft());
        Const right = get(binary.getRight());
        if (left.isOverdefined() || right.isOverdefined()) {
            return Const.OVERDEFINED;
        }
        if (!left.isConst() || !right.isConst()) {
            return Const.UNDEF;
        }
        try {
            switch (binary.getOp()) {
                case PLUS:
                    return constResult(Binaries.plus(left.value, right.value));
                case MINUS:
                    return constResult(Binaries.minus(left.value, right.value));
                case MULTIPLY:
                    return constResult(Binaries.multiply(left.value, right.value));
                case DIVIDE:
                    return constResult(Binaries.divide(left.value, right.value));
                case MOD:
                    return constResult(Binaries.modulo(left.value, right.value));
                case MATCH:
                    return constResult(Binaries.matches(left.value, right.value));
                case LT:
                    return constResult(Binaries.lt(left.value, right.value));
                case LTE:
                    return constResult(Binaries.lte(left.value, right.value));
                case GT:
                    return constResult(Binaries.gt(left.value, right.value));
                case GTE:
                    return constResult(Binaries.gte(left.value, right.value));
                case EQ:
                    return constResult(Binaries.eq(left.value, right.value));
                case SEQ:
                    return constResult(Binaries.seq(left.value, right.value));
                case NE:
                    return constResult(Binaries.ne(left.value, right.value));
                case SNE:
                    return constResult(Binaries.sne(left.value, right.value));
                case IN:
                    return constResult(Binaries.in(left.value, right.value));
                default:
                    return Const.OVERDEFINED;
            }
        } catch (Exception e) {
            return Const.OVERDEFINED;
        }
    }

    private Const evaluatePropGet(PropGet propGet) {
        Const owner = get(propGet.getOwner());
        if (!owner.isConst()) {
            return owner;
        }
        return owner.value.isObject() ? Const.OVERDEFINED : Const.of(MissingNode.getInstance());
    }

    private Const evaluateIndexGet(IndexGet indexGet) {
        Const owner = get(indexGet.getOwner());
        if (!owner.isConst()) {
            return owner;
        }
        ValueNode ownerValue = owner.value;
        if (ownerValue.isObject() || ownerValue.isArray() || ownerValue.isTextual()) {
            return Const.OVERDEFINED;
        }
        return Const.of(MissingNode.getInstance());
    }

    private static Const constResult(JsonNode node) {
        return node instanceof ValueNode ? Const.of((ValueNode) node) : Const.OVERDEFINED;
    }

    private Const get(SsaValue value) {
        ValueNode constantValue = ConstantValues.valueOf(value);
        if (constantValue != null) {
            return Const.of(constantValue);
        }
        Const constant = constants.get(value);
        return constant == null ? Const.UNDEF : constant;
    }

    private static Const merge(Const left, Const right) {
        if (left.isUndef()) {
            return right;
        }
        if (right.isUndef()) {
            return left;
        }
        if (left.isOverdefined() || right.isOverdefined()) {
            return Const.OVERDEFINED;
        }
        return Objects.equals(left.value, right.value) ? left : Const.OVERDEFINED;
    }

    private static final class Const {
        static final Const UNDEF = new Const(0, null);
        static final Const OVERDEFINED = new Const(1, null);

        final int kind;
        final ValueNode value;

        private Const(int kind, ValueNode value) {
            this.kind = kind;
            this.value = value;
        }

        static Const of(ValueNode value) {
            return new Const(2, value);
        }

        boolean isUndef() {
            return kind == 0;
        }

        boolean isOverdefined() {
            return kind == 1;
        }

        boolean isConst() {
            return kind == 2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Const)) {
                return false;
            }
            Const aConst = (Const) o;
            return kind == aConst.kind && Objects.equals(value, aConst.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, value);
        }
    }
}
