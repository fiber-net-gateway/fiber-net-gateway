package io.fiber.net.script.aot;

public class MaybePhi extends Expr {
    private final int idx;
    private final boolean stack;

    protected MaybePhi(Block belongTo, int pc, int idx, boolean stack) {
        super(belongTo, pc);
        this.idx = idx;
        this.stack = stack;
    }

    public int getIdx() {
        return idx;
    }

    public boolean isStack() {
        return stack;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }
}
