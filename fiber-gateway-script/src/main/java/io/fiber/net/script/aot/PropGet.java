package io.fiber.net.script.aot;

public class PropGet extends Expr {
    SsaValue owner;
    final String key;

    protected PropGet(Block belongTo, int pc, SsaValue owner, String key) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        owner.addUsed(this);
    }

    public SsaValue getOwner() {
        return owner;
    }

    public String getKey() {
        return key;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        if (owner != oldVal) {
            return 0;
        }
        owner = newVal;
        return 1;
    }

    @Override
    void dropOperands() {
        owner.removeUsed(this);
    }

    @Override
    public SsaValue.Type getResultType() {
        SsaValue.Type type = owner.getType();
        if (type == SsaValue.Type.Unknown) {
            return SsaValue.Type.Unknown;
        }

        if (type != SsaValue.Type.OBJECT) {
            return SsaValue.Type.MISSING;
        }

        return SsaValue.Type.Unknown;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }

    @Override
    public int effects() {
        return EFFECT_PURE | EFFECT_MEMORY_READ;
    }
}
