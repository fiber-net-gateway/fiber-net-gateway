package io.fiber.net.script.parse.ir;

class LoadVar extends Exp implements VarLoad {
    private final int varIdx;

    LoadVar(int varIdx) {
        this.varIdx = varIdx;
    }

    static LoadVar of(int varIdx) {
        return new LoadVar(varIdx);
    }

    @Override
    public int getLoadIdx() {
        return varIdx;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitLoadVar(this);
    }
}
