package io.fiber.net.script.aot;

public class IndexSet1 extends Expr {
    final SsaValue owner;
    final SsaValue key;
    final SsaValue alien;

    protected IndexSet1(Block belongTo, int pc, SsaValue owner, SsaValue key, SsaValue alien) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        this.alien = alien;
        owner.addUsed(this);
        key.addUsed(this);
        alien.addUsed(this);
    }

    @Override
    public SsaValue.Type getResultType() {
        return owner.getType();
    }

    public SsaValue getOwner() {
        return owner;
    }

    public SsaValue getKey() {
        return key;
    }

    public SsaValue getAlien() {
        return alien;
    }
}
