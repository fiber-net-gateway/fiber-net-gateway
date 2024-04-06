package io.fiber.net.script.parse.ir;

class IndexSet extends Exp {
    private final Exp parent;
    private final Exp key;
    private final Exp alien;

    IndexSet(Exp parent, Exp key, Exp alien) {
        this.parent = parent;
        this.key = key;
        this.alien = alien;
    }

    public Exp getParent() {
        return parent;
    }

    public Exp getKey() {
        return key;
    }

    public Exp getAlien() {
        return alien;
    }

    public static IndexSet of(Exp parent, Exp key, Exp alien) {
        return new IndexSet(parent, key, alien);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitIndexSet(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.indexSet();
        return -2;
    }
}
