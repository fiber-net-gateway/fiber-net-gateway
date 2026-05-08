package io.fiber.net.script.aot;

public abstract class Instruction {
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
}
