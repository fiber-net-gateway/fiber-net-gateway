package io.fiber.net.script.parse.ir;

class PropGet extends Exp {
    private final Exp parent;
    private final int keyId;

    PropGet(Exp parent, int keyId) {
        this.parent = parent;
        this.keyId = keyId;
    }

    public Exp getParent() {
        return parent;
    }

    public int getKeyId() {
        return keyId;
    }

    public static PropGet of(Exp parent, int keyId) {
        return new PropGet(parent, keyId);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitPropGet(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.propGet(getKeyId());
        return 0;
    }
}
