package io.fiber.net.script.aot;

public class ExpandArr extends Instruction {

    SsaValue target;
    SsaValue addition;

    protected ExpandArr(Block belongTo, int pc, SsaValue target, SsaValue addition) {
        super(belongTo, pc);
        this.target = target;
        this.addition = addition;
        target.addUsed(this);
        addition.addUsed(this);
    }

    public SsaValue getTarget() {
        return target;
    }

    public SsaValue getAddition() {
        return addition;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        if (target == oldVal) {
            target = newVal;
            replaced++;
        }
        if (addition == oldVal) {
            addition = newVal;
            replaced++;
        }
        return replaced;
    }

    @Override
    void dropOperands() {
        target.removeUsed(this);
        addition.removeUsed(this);
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }

    @Override
    public int effects() {
        return EFFECT_MEMORY_WRITE;
    }
}
