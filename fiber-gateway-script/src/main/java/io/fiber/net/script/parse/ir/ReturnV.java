package io.fiber.net.script.parse.ir;

class ReturnV extends Instrument {

    static ReturnV of() {
        return new ReturnV();
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.returnV();
        return 0;
    }
}
