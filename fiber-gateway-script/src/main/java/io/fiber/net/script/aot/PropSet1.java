package io.fiber.net.script.aot;

public class PropSet1 extends Expr {
    final SsaValue owner;
    final String key;
    final SsaValue alien;

    protected PropSet1(Block belongTo, int pc, SsaValue owner, String key, SsaValue alien) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        this.alien = alien;
    }

    @Override
    public SsaValue.Type getResultType() {
        return owner.getType();
    }

    public SsaValue getOwner() {
        return owner;
    }

    public String getKey() {
        return key;
    }

    public SsaValue getAlien() {
        return alien;
    }

}
