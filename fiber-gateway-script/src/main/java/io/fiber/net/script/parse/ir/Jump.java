package io.fiber.net.script.parse.ir;

public class Jump extends Instrument {
    private final CodeEnterPoint target;

    public Jump(CodeEnterPoint target) {
        this.target = target;
    }

    public CodeEnterPoint getTarget() {
        return target;
    }

    public static Jump of(CodeEnterPoint target) {
        return new Jump(target);
    }


    @Override
    int assemble(ClzAssembler assembler) {
        assembler.jump(getTarget());
        return 0;
    }
}
