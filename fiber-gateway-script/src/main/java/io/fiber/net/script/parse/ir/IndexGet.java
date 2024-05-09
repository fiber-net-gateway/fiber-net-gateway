package io.fiber.net.script.parse.ir;

class IndexGet extends Exp {
    private final Exp parent;
    private final Exp key;

    IndexGet(Exp parent, Exp key) {
        this.parent = parent;
        this.key = key;
    }

    public Exp getParent() {
        return parent;
    }

    public Exp getKey() {
        return key;
    }


    public static IndexGet of(Exp parent, Exp key) {
        return new IndexGet(parent, key);
    }


    @Override
    int assemble(ClzAssembler assembler) {
        assembler.indexGet();
        return -1;
    }
}
