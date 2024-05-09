package io.fiber.net.script.parse.ir;

class IterateInto extends VarStore {
    private final Exp tobe;

    IterateInto(Exp tobe, int varIdx) {
        super(varIdx);
        this.tobe = tobe;
    }

    Exp getTobe() {
        return tobe;
    }

    public static IterateInto of(Exp tobe, int varIdx) {
        return new IterateInto(tobe, varIdx);
    }


    @Override
    int assemble(ClzAssembler assembler) {
        assembler.iterateInto(getStoreVar());
        return -1;
    }
}
