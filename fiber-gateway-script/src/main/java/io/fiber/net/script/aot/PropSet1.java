package io.fiber.net.script.aot;

public class PropSet1 extends Expr {
    SsaValue owner;
    final String key;
    SsaValue alien;

    protected PropSet1(Block belongTo, int pc, SsaValue owner, String key, SsaValue alien) {
        super(belongTo, pc);
        this.owner = owner;
        this.key = key;
        this.alien = alien;
        owner.addUsed(this);
        alien.addUsed(this);
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

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        if (owner == oldVal) {
            owner = newVal;
            replaced++;
        }
        if (alien == oldVal) {
            alien = newVal;
            replaced++;
        }
        return replaced;
    }

    @Override
    void dropOperands() {
        owner.removeUsed(this);
        alien.removeUsed(this);
    }

    @Override
    public Throw canThrow() {
        switch (owner.getType()) {
            case OBJECT:
                return Throw.NOT;
            case Unknown:
                return Throw.MAYBE;
            default:
                return Throw.ALWAYS;
        }
    }

    @Override
    public int effects() {
        return EFFECT_MEMORY_WRITE;
    }
}
