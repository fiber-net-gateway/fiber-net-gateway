package io.fiber.net.script.aot;

public class Ret extends Instruction {

    final SsaValue value;

    protected Ret(Block belongTo, int pc, SsaValue value) {
        super(belongTo, pc);
        this.value = value;
        value.addUsed(this);
    }

    public SsaValue getValue() {
        return value;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
