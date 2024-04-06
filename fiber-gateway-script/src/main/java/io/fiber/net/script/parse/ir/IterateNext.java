package io.fiber.net.script.parse.ir;

class IterateNext extends Exp implements VarLoad {

    private final int varIdx;

    IterateNext(int varIdx) {
        this.varIdx = varIdx;
    }

    public static IterateNext of(int varIdx) {
        return new IterateNext(varIdx);
    }

    @Override
    public int getLoadIdx() {
        return varIdx;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitIterateNext(this);
    }
}
