package io.fiber.net.script.aot;

public class PropGet extends Expr {
    final SsaValue owner;
    final String key;

    protected PropGet(Block belongTo, int pc, SsaValue owner, String key) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
    }

    public SsaValue getOwner() {
        return owner;
    }

    public String getKey() {
        return key;
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
}
