package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallAsyncFunc extends Expr {

    final Library.AsyncFunction function;
    final boolean spread;

    protected CallAsyncFunc(Block belongTo, int pc, Library.AsyncFunction function, boolean spread) {
        super(belongTo, pc);
        this.function = function;
        this.spread = spread;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }

    public Library.AsyncFunction getFunction() {
        return function;
    }

    public boolean isSpread() {
        return spread;
    }
}
