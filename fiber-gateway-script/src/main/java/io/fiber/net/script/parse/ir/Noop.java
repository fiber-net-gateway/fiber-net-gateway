package io.fiber.net.script.parse.ir;

class Noop extends Instrument {
    static final Noop INSTANCE = new Noop();

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitNoop(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        return 0;
    }
}
