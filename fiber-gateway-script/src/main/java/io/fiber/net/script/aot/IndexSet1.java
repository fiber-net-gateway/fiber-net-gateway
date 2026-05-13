package io.fiber.net.script.aot;

public class IndexSet1 extends Expr {
    SsaValue owner;
    SsaValue key;
    SsaValue alien;

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

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        if (owner == oldVal) {
            owner = newVal;
            replaced++;
        }
        if (key == oldVal) {
            key = newVal;
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
        key.removeUsed(this);
        alien.removeUsed(this);
    }

    @Override
    public Throw canThrow() {
        switch (owner.getType()) {
            case OBJECT:
                return IndexSet.objectKeyThrow(key);
            case ARRAY:
                return IndexSet.arrayKeyThrow(key);
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
