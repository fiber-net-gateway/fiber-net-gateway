package io.fiber.net.script.aot;

public class ExpandArr extends Instruction {

    final SsaValue target;
    final SsaValue addition;

    protected ExpandArr(Block belongTo, int pc, SsaValue target, SsaValue addition) {
        super(belongTo, pc);
        this.target = target;
        this.addition = addition;
    }

    public SsaValue getTarget() {
        return target;
    }

    public SsaValue getAddition() {
        return addition;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
