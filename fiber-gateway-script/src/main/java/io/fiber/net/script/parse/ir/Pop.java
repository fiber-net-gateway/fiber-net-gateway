package io.fiber.net.script.parse.ir;

public class Pop extends Instrument {
    private boolean requirePop;

    public Pop(boolean requirePop) {
        this.requirePop = requirePop;
    }

    static Pop of(boolean requirePop) {
        return new Pop(requirePop);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitPop(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        if (requirePop) {
            assembler.pop();
        }
        return -1;
    }
}
