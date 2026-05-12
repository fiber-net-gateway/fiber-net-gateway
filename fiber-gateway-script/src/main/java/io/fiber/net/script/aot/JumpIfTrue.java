package io.fiber.net.script.aot;

public class JumpIfTrue extends Instruction {
    final Block target;
    SsaValue cond;

    protected JumpIfTrue(Block belongTo, int pc, Block target, SsaValue cond) {
        super(belongTo, pc);
        this.target = target;
        this.cond = cond;
        cond.addUsed(this);
    }

    public Block getTarget() {
        return target;
    }

    public SsaValue getCond() {
        return cond;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        if (cond != oldVal) {
            return 0;
        }
        cond = newVal;
        return 1;
    }

    @Override
    void dropOperands() {
        cond.removeUsed(this);
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
