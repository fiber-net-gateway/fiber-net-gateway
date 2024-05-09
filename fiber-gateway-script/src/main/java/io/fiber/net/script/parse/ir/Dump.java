package io.fiber.net.script.parse.ir;

class Dump extends Exp {
    private final StackRef ref;

    Dump(StackRef other) {
        this.ref = other;
    }

    public StackRef getRef() {
        return ref;
    }

    static Dump of(Exp exp) {
        return new Dump(StackRef.of(exp));
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.dump();
        return 1;
    }
}
