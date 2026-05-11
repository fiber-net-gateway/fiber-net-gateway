package io.fiber.net.script.aot;

public class IndexGet extends Expr {
    final SsaValue owner;
    final SsaValue key;

    protected IndexGet(Block belongTo, int pc, SsaValue owner, SsaValue key) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        owner.addUsed(this);
        key.addUsed(this);
    }

    public SsaValue getOwner() {
        return owner;
    }

    public SsaValue getKey() {
        return key;
    }

    @Override
    public SsaValue.Type getResultType() {
        SsaValue.Type type = owner.getType();
        if (type == SsaValue.Type.Unknown
                || key.getType() == SsaValue.Type.Unknown) {
            return SsaValue.Type.Unknown;
        }

        if (type != SsaValue.Type.OBJECT
                && type != SsaValue.Type.ARRAY
                && type != SsaValue.Type.STRING) {
            return SsaValue.Type.MISSING;
        }

        return SsaValue.Type.Unknown;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
