package io.fiber.net.script.aot;

public class CatchError extends Expr {
    protected CatchError(Block belongTo, int pc) {
        super(belongTo, pc);
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.EXCEPTION;
    }

    @Override
    public Throw canThrow() {
        return Throw.NOT;
    }
}
