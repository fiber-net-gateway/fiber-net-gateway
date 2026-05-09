package io.fiber.net.script.aot;

public class IndexSet extends Expr {
    final SsaValue owner;
    final SsaValue key;
    final SsaValue alien;

    protected IndexSet(Block belongTo, int pc, SsaValue owner, SsaValue key, SsaValue alien) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        this.alien = alien;
    }

    @Override
    public SsaValue.Type getResultType() {
        return alien.getType();
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
