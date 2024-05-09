package io.fiber.net.script.parse.ir;

class Noop extends Instrument {
    static final Noop INSTANCE = new Noop();

    @Override
    int assemble(ClzAssembler assembler) {
        return 0;
    }
}
