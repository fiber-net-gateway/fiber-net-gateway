package io.fiber.net.script.aot;

public class LoadRoot extends Expr {
    protected LoadRoot(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }
}
