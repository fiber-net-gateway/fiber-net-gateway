package io.fiber.net.script.parse.ir;

public class LoadRoot extends Exp {
    public static LoadRoot of() {
        return new LoadRoot();
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitLoadRoot(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.loadRoot();
        return 1;
    }
}
