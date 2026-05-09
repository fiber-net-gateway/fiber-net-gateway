package io.fiber.net.script.aot;

public class Throw extends Instruction {
    final SsaValue value;

    protected Throw(Block belongTo, int pc, SsaValue value) {
        super(belongTo, pc);
        this.value = value;
    }

    @Override
    public Throw canThrow() {
        return Throw.ALWAYS;
    }
}
