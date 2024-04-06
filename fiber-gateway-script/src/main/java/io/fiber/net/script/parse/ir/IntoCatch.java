package io.fiber.net.script.parse.ir;

class IntoCatch extends VarStore {
    IntoCatch(int expIndex) {
        super(expIndex);
    }

    public static IntoCatch of(int expIdx) {
        return new IntoCatch(expIdx);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitIntoCatch(this);
    }
}
