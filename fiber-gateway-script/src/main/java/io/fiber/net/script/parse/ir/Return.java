package io.fiber.net.script.parse.ir;

class Return extends Instrument {
    private final Exp target;

    Return(Exp target) {
        this.target = target;
    }

    Exp getTarget() {
        return target;
    }

    public static Return of(Exp target) {
        return new Return(target);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.returnResult();
        return -1;
    }
}
