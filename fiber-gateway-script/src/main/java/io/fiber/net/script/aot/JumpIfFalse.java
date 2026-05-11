package io.fiber.net.script.aot;

public class JumpIfFalse extends Instruction {
    final Block target;
    final SsaValue cond;

    protected JumpIfFalse(Block belongTo, int pc, Block target, SsaValue cond) {
        super(belongTo, pc);
        this.target = target;
        this.cond = cond;
        cond.addUsed(this);
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
