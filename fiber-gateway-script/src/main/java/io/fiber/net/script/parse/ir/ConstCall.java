package io.fiber.net.script.parse.ir;

class ConstCall extends Exp {
    private final int constId;
    private final int asyncPoint;
    private int prevStackSize;


    ConstCall(int constId, int asyncPoint) {
        this.constId = constId;
        this.asyncPoint = asyncPoint;
    }

    public static ConstCall of(int constId, int asyncPoint) {
        return new ConstCall(constId, asyncPoint);
    }

    public int getConstId() {
        return constId;
    }

    public boolean isAsync() {
        return asyncPoint >= 0;
    }

    int getAsyncPoint() {
        return asyncPoint;
    }

    int getPrevStackSize() {
        return prevStackSize;
    }

    void setPrevStackSize(int prevStackSize) {
        this.prevStackSize = prevStackSize;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitConstCall(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        if (isAsync()) {
            for (int i = getPrevStackSize() - 1; i >= 0; i--) {
                assembler.stashStack(i);
            }
            assembler.asyncConstCall(this);
        } else {
            assembler.constCall(this);
        }
        return 1;
    }
}
