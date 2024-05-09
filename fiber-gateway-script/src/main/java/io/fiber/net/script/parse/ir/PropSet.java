package io.fiber.net.script.parse.ir;

class PropSet extends Exp {
    private final Exp parent;
    private final int keyId;
    private final Exp alien;

    PropSet(Exp parent, int keyId, Exp alien) {
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

    public static PropSet of(Exp parent, int keyId, Exp alien) {
        return new PropSet(parent, keyId, alien);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.propSet(getKeyId());
        return -1;
    }
}
