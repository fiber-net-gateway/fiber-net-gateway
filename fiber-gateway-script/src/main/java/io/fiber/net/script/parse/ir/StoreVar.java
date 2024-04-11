package io.fiber.net.script.parse.ir;

class StoreVar extends VarStore {
    private final Exp value;

    public Exp getValue() {
        return value;
    }

    public StoreVar(int varIdx, Exp value) {
        super(varIdx);
        this.value = value;
    }

    public static StoreVar of(int varIdx, Exp value) {
        return new StoreVar(varIdx, value);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitStoreVar(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.storeVar(getStoreVar());
        return -1;
    }
}
