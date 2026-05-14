package io.fiber.net.script.aot;

public class RetV extends Instruction {


    protected RetV(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
