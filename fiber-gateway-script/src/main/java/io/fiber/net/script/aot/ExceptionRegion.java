package io.fiber.net.script.aot;

public final class ExceptionRegion {
    private final int tryStartPc;
    private final int tryEndPc;
    private final int catchStartPc;
    private final int catchEndPc;
    private final BasicBlock catchBlock;

    ExceptionRegion(int tryStartPc, int tryEndPc, int catchStartPc, int catchEndPc, BasicBlock catchBlock) {
        this.tryStartPc = tryStartPc;
        this.tryEndPc = tryEndPc;
        this.catchStartPc = catchStartPc;
        this.catchEndPc = catchEndPc;
        this.catchBlock = catchBlock;
    }

    public int tryStartPc() {
        return tryStartPc;
    }

    public int tryEndPc() {
        return tryEndPc;
    }

    public int catchStartPc() {
        return catchStartPc;
    }

    public int catchEndPc() {
        return catchEndPc;
    }

    public BasicBlock catchBlock() {
        return catchBlock;
    }

    public boolean containsTryPc(int pc) {
        return pc >= tryStartPc && pc < tryEndPc;
    }

    @Override
    public String toString() {
        return "try[" + tryStartPc + ',' + tryEndPc + ") catch[" + catchStartPc + ',' + catchEndPc + ')';
    }
}
