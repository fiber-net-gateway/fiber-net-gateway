package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.run.Binaries;

public class Binary extends Expr {

    public enum Op {
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        MOD,
        MATCH,

        LT,
        LTE,
        GT,
        GTE,
        EQ,
        SEQ,
        NE,
        SNE,
        IN,
    }

    final Op op;
    SsaValue left;
    SsaValue right;

    public Binary(Block belongTo, int pc, Op op, SsaValue left, SsaValue right) {
        super(belongTo, pc);
        this.op = op;
        this.left = left;
        this.right = right;
        left.addUsed(this);
        right.addUsed(this);
    }

    public Op getOp() {
        return op;
    }

    public SsaValue getLeft() {
        return left;
    }

    public SsaValue getRight() {
        return right;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        if (left == oldVal) {
            left = newVal;
            replaced++;
        }
        if (right == oldVal) {
            right = newVal;
            replaced++;
        }
        return replaced;
    }

    @Override
    void dropOperands() {
        left.removeUsed(this);
        right.removeUsed(this);
    }

    @Override
    public SsaValue.Type getResultType() {
        assert op != null;
        if (op == Op.PLUS) {
            if (left.getType() == SsaValue.Type.STRING ||
                    right.getType() == SsaValue.Type.STRING) {
                return SsaValue.Type.STRING;
            }
            if (left.getType() == SsaValue.Type.NUMBER &&
                    right.getType() == SsaValue.Type.NUMBER) {
                return SsaValue.Type.NUMBER;
            }
            return SsaValue.Type.Unknown;
        }

        if (op.ordinal() < Op.MATCH.ordinal()) {
            return SsaValue.Type.NUMBER;
        }
        return SsaValue.Type.BOOLEAN;
    }

    @Override
    public Throw canThrow() {
        switch (op) {
            case PLUS:
                return plusThrow();
            case MINUS:
            case MULTIPLY:
                return numericCoercionThrow();
            case DIVIDE:
            case MOD:
                return numericOnlyThrow();
            case MATCH:
            case LT:
            case LTE:
            case GT:
            case GTE:
            case EQ:
            case SEQ:
            case NE:
            case SNE:
            case IN:
                return Throw.NOT;
            default:
                throw new IllegalStateException("[bug] not hit");
        }
    }

    @Override
    public int effects() {
        return EFFECT_PURE;
    }

    private Throw plusThrow() {
        Throw constant = constantThrow();
        if (constant != null) {
            return constant;
        }

        SsaValue.Type leftType = left.getType();
        SsaValue.Type rightType = right.getType();
        if (leftType == SsaValue.Type.STRING || rightType == SsaValue.Type.STRING) {
            return Throw.NOT;
        }
        if (leftType == SsaValue.Type.NUMBER && rightType == SsaValue.Type.NUMBER) {
            return Throw.NOT;
        }
        if (leftType == SsaValue.Type.Unknown || rightType == SsaValue.Type.Unknown) {
            return Throw.MAYBE;
        }
        return Throw.ALWAYS;
    }

    private Throw numericCoercionThrow() {
        Throw constant = constantThrow();
        if (constant != null) {
            return constant;
        }
        return merge(numericCoercionThrow(left), numericCoercionThrow(right), false);
    }

    private Throw numericOnlyThrow() {
        Throw constant = constantThrow();
        if (constant != null) {
            return constant;
        }
        return merge(numericOnlyThrow(left), numericOnlyThrow(right), true);
    }

    private Throw constantThrow() {
        ValueNode leftConst = constantValue(left);
        ValueNode rightConst = constantValue(right);
        if (leftConst == null || rightConst == null) {
            return null;
        }

        try {
            switch (op) {
                case PLUS:
                    Binaries.plus(leftConst, rightConst);
                    break;
                case MINUS:
                    Binaries.minus(leftConst, rightConst);
                    break;
                case MULTIPLY:
                    Binaries.multiply(leftConst, rightConst);
                    break;
                case DIVIDE:
                    Binaries.divide(leftConst, rightConst);
                    break;
                case MOD:
                    Binaries.modulo(leftConst, rightConst);
                    break;
                default:
                    return Throw.NOT;
            }
            return Throw.NOT;
        } catch (Exception e) {
            return Throw.ALWAYS;
        }
    }

    private static Throw numericCoercionThrow(SsaValue value) {
        ValueNode constant = constantValue(value);
        if (constant != null) {
            return canCoerceToNumber(constant) ? Throw.NOT : Throw.ALWAYS;
        }
        switch (value.getType()) {
            case NUMBER:
                return Throw.NOT;
            case STRING:
            case Unknown:
                return Throw.MAYBE;
            default:
                return Throw.ALWAYS;
        }
    }

    private static Throw numericOnlyThrow(SsaValue value) {
        ValueNode constant = constantValue(value);
        if (constant != null) {
            return constant.isNumber() ? Throw.NOT : Throw.ALWAYS;
        }
        switch (value.getType()) {
            case NUMBER:
                return Throw.NOT;
            case Unknown:
                return Throw.MAYBE;
            default:
                return Throw.ALWAYS;
        }
    }

    private static Throw merge(Throw left, Throw right, boolean maybeArithmeticError) {
        if (left == Throw.ALWAYS || right == Throw.ALWAYS) {
            return Throw.ALWAYS;
        }
        if (left == Throw.NOT && right == Throw.NOT) {
            return maybeArithmeticError ? Throw.MAYBE : Throw.NOT;
        }
        return Throw.MAYBE;
    }

    private static boolean canCoerceToNumber(ValueNode value) {
        if (value.isNumber()) {
            return true;
        }
        if (!value.isTextual()) {
            return false;
        }
        try {
            return AstUtils.tryToNumber(value.asText()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ValueNode constantValue(SsaValue value) {
        return ConstantValues.valueOf(value);
    }
}
