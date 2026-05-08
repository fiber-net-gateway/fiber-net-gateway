package io.fiber.net.script.aot;

public class NewObj extends Expr {
    protected NewObj(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.OBJECT;
    }
}
