package io.fiber.net.script.parse.ir;

class ConstCall extends Exp {
    private final int constId;
    private final boolean async;

    ConstCall(int constId, boolean async) {
        this.constId = constId;
        this.async = async;
    }

    public static ConstCall of(int constId, boolean async) {
        return new ConstCall(constId, async);
    }

    public int getConstId() {
        return constId;
    }

    public boolean isAsync() {
        return async;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitConstCall(this);
    }
}
