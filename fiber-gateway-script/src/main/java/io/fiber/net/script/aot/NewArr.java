package io.fiber.net.script.aot;

public class NewArr extends Expr {
    protected NewArr(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.ARRAY;
    }
}
