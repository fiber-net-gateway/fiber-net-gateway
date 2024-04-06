package io.fiber.net.script.parse.ir;

class Throw extends Instrument {
    private final Exp target;

    Throw(Exp target) {
        this.target = target;
    }

    Exp getTarget() {
        return target;
    }

    public static Throw of(Exp target) {
        return new Throw(target);
    }
    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitThrow(this);
    }
}
