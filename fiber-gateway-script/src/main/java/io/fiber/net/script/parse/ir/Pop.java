package io.fiber.net.script.parse.ir;

public class Pop extends Instrument {
    public static Pop of() {
        return new Pop();
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitPop(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.pop();
        return -1;
    }
}
