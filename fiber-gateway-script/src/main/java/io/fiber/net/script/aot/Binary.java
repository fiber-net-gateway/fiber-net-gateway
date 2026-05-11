package io.fiber.net.script.aot;

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
        return op.ordinal() < Op.MATCH.ordinal() ? Throw.MAYBE : Throw.NOT;
    }
}
