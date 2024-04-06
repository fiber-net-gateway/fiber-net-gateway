package io.fiber.net.script.parse.ir;

class ReturnV extends Instrument {

    static ReturnV of() {
        return new ReturnV();
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitReturnV(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.returnV();
        return 0;
    }
}
