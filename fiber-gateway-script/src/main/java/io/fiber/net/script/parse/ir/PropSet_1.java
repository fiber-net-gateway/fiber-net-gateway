package io.fiber.net.script.parse.ir;

class PropSet_1 extends Exp {
    private final Exp parent;
    private final int keyId;
    private final Exp alien;

    PropSet_1(Exp parent, int keyId, Exp alien) {
        this.parent = parent;
        this.keyId = keyId;
        this.alien = alien;
    }

    public Exp getParent() {
        return parent;
    }

    public int getKeyId() {
        return keyId;
    }

    public Exp getAlien() {
        return alien;
    }

    public static PropSet_1 of(Exp parent, int keyId, Exp alien) {
        return new PropSet_1(parent, keyId, alien);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitPropSet_1(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        // parent, alien, string
        assembler.propSet1(getKeyId());
        return -1;
    }
}
