package io.fiber.net.script.aot;

public class Unary extends Expr {

    public enum Op {
        PLUS,
        MINUS,
        NEG,
        TYPEOF,

        ITERATE_INTO,
        ITERATE_NEXT,
        ITERATE_KEY,
        ITERATE_VALUE,

    }

    final Op op;
    SsaValue material;

    public Unary(Block belongTo, int pc, Op op, SsaValue material) {
        super(belongTo, pc);
        this.op = op;
        this.material = material;
        material.addUsed(this);
    }

    public Op getOp() {
        return op;
    }

    public SsaValue getMaterial() {
        return material;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        if (material != oldVal) {
            return 0;
        }
        material = newVal;
        return 1;
    }

    @Override
    void dropOperands() {
        material.removeUsed(this);
    }

    @Override
    public SsaValue.Type getResultType() {
        switch (op) {
            case PLUS:
            case MINUS:
                return SsaValue.Type.NUMBER;
            case NEG:
            case ITERATE_NEXT:
                return SsaValue.Type.BOOLEAN;
            case TYPEOF:
                return SsaValue.Type.STRING;
            case ITERATE_INTO:
                return SsaValue.Type.ITERATOR;
            case ITERATE_KEY: {
                SsaValue.Type type = material.getType();
                if (type == SsaValue.Type.ARRAY) {
                    return SsaValue.Type.NUMBER;
                } else if (type == SsaValue.Type.OBJECT) {
                    return SsaValue.Type.STRING;
                }
                return SsaValue.Type.Unknown;
            }
            case ITERATE_VALUE:
                return SsaValue.Type.Unknown;
            default:
                throw new IllegalStateException("[bug] not hit");
        }
    }

    @Override
    public Throw canThrow() {
        switch (op) {
            case PLUS:
            case MINUS:
                return Throw.MAYBE;
            case NEG:
            case ITERATE_NEXT:
            case TYPEOF:
            case ITERATE_INTO:
            case ITERATE_KEY:
            case ITERATE_VALUE:
                return Throw.NOT;
            default:
                throw new IllegalStateException("[bug] not hit");
        }
    }
}
