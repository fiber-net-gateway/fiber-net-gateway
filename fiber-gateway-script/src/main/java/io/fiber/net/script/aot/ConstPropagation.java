package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Unaries;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public class ConstPropagation {

    private final Cfg cfg;
    private final Map<SsaValue, Const> constants = new IdentityHashMap<>();

    public ConstPropagation(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        analyze();
        return rewrite();
    }

    private void analyze() {
        ArrayDeque<Instruction> queue = new ArrayDeque<>();
        for (Block block : cfg.getBlocks()) {
            queue.addAll(block.getPhiValues());
            queue.addAll(block.getInstructions());
        }

        while (!queue.isEmpty()) {
            Instruction instruction = queue.poll();
            if (!(instruction instanceof Expr)) {
                continue;
            }
            Expr expr = (Expr) instruction;
            SsaValue result = expr.getResult();
            Const oldConst = get(result);
            Const newConst = evaluate(expr);
            if (oldConst.equals(newConst)) {
                continue;
            }
            constants.put(result, newConst);
            queue.addAll(result.getUsed());
        }
    }

    private boolean rewrite() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
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
                replaceValue(phi.getResult(), replacement);
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
                LoadConst replacement = new LoadConst(block, instruction.getPc(), constant.value);
                instruction.dropOperands();
                expr.getResult().replaceAssign(replacement);
                block.replaceInstruction(instruction, replacement);
                changed = true;
            }
        }
        return changed;
    }

    private SsaValue findConstReplacement(Phi phi, ValueNode value) {
        for (Phi.Case aCase : phi.getCases()) {
            if (aCase.value == phi.getResult()) {
                continue;
            }
            Const constant = get(aCase.value);
            if (constant.isConst() && Objects.equals(constant.value, value)) {
                return aCase.value;
            }
        }
        return null;
    }

    private void replaceValue(SsaValue oldVal, SsaValue newVal) {
        oldVal.replaceAllUsesWith(newVal);
        for (Block block : cfg.getBlocks()) {
            block.replaceFrameValue(oldVal, newVal);
        }
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
            merged = merge(merged, get(aCase.value));
            if (merged.isOverdefined()) {
                return merged;
            }
        }
        return merged;
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
