package io.fiber.net.script.parse.ir;

abstract class Instrument {
    /**
     * @param assembler assembler
     * @return stackChange
     */
    abstract int assemble(ClzAssembler assembler);
}
