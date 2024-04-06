package io.fiber.net.script.parse.ir;

class LoadConst extends Exp {
    private final int constValIdx;

    public LoadConst(int constValIdx) {
        this.constValIdx = constValIdx;
    }

    int getConstValIdx() {
        return constValIdx;
    }

    public static LoadConst of(int constValIdx) {
        return new LoadConst(constValIdx);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitLoadConst(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.loadConst(getConstValIdx());
        return 1;
    }
}
