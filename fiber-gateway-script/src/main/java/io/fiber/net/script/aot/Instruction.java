package io.fiber.net.script.aot;

public abstract class Instruction {
    static final int EFFECT_PURE = 1;
    static final int EFFECT_MEMORY_READ = 1 << 1;
    static final int EFFECT_MEMORY_WRITE = 1 << 2;
    static final int EFFECT_CALL = 1 << 3;

    public enum Throw {
        NOT,
        MAYBE,
        ALWAYS,
    }

    protected Block belongTo;
    protected final int pc;

    protected Instruction(Block belongTo, int pc) {
        this.belongTo = belongTo;
        this.pc = pc;
    }

    public Block getBelongTo() {
        return belongTo;
    }

    void moveTo(Block block) {
        belongTo = block;
    }

    public int getPc() {
        return pc;
    }

    public Throw canThrow() {
        return Throw.MAYBE;
    }

    public int effects() {
        return 0;
    }

    public boolean isPure() {
        return (effects() & EFFECT_PURE) != 0;
    }

    public boolean isMemoryBarrier() {
        int effects = effects();
        return (effects & (EFFECT_MEMORY_WRITE | EFFECT_CALL)) != 0;
    }

    public boolean isRemovablePure() {
        return canThrow() == Throw.NOT && isPure();
    }

    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        return 0;
    }

    void dropOperands() {
    }
}
