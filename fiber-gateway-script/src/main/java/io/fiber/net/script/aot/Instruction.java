package io.fiber.net.script.aot;

public abstract class Instruction {
    public enum Throw {
        NOT,
        MAYBE,
        ALWAYS,
    }

    protected final Block belongTo;
    protected final int pc;

    protected Instruction(Block belongTo, int pc) {
        this.belongTo = belongTo;
        this.pc = pc;
    }

    public Block getBelongTo() {
        return belongTo;
    }

    public int getPc() {
        return pc;
    }

    public Throw canThrow() {
        return Throw.MAYBE;
    }
}
