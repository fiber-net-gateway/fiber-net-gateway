package io.fiber.net.script.aot;

public class Throw extends Instruction {
    SsaValue value;

    protected Throw(Block belongTo, int pc, SsaValue value) {
        super(belongTo, pc);
        this.value = value;
        value.addUsed(this);
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        if (value != oldVal) {
            return 0;
        }
        value = newVal;
        return 1;
    }

    @Override
    public Throw canThrow() {
        return Throw.ALWAYS;
    }
}
