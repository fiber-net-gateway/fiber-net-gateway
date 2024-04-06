package io.fiber.net.script.parse.ir;

class IterateVar extends VarStore implements VarLoad {

    private final int iteratorIdx;
    private final boolean key;

    @Override
    public int getLoadIdx() {
        return iteratorIdx;
    }

    IterateVar(int iteratorIdx, int storeIdx, boolean key) {
        super(storeIdx);
        this.iteratorIdx = iteratorIdx;
        this.key = key;
    }

    boolean isKey() {
        return key;
    }

    public static IterateVar of(int iteratorIdx, int storeIdx, boolean key) {
        return new IterateVar(iteratorIdx, storeIdx, key);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitIterateVar(this);
    }
}
