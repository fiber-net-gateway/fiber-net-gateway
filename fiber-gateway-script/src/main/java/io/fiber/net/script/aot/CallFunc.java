package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallFunc extends Expr {

    final Library.Function function;
    final boolean spread;

    protected CallFunc(Block belongTo, int pc, Library.Function function, boolean spread) {
        super(belongTo, pc);
        this.function = function;
        this.spread = spread;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }

    public Library.Function getFunction() {
        return function;
    }

    public boolean isSpread() {
        return spread;
    }
}
