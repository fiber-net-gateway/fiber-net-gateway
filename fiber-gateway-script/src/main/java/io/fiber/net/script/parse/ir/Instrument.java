package io.fiber.net.script.parse.ir;

abstract class Instrument {
    abstract void accept(InstrumentVisitor visitor);

    /**
     * @param assembler assembler
     * @return stackChange
     */
    int assemble(ClzAssembler assembler) {
        throw new UnsupportedOperationException("not implements");
    }
}
