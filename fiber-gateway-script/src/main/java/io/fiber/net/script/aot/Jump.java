package io.fiber.net.script.aot;

public class Jump extends Instruction {
    final Block target;

    protected Jump(Block belongTo, int pc, Block target) {
        super(belongTo, pc);
        this.target = target;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
