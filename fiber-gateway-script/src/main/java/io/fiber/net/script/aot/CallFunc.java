package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallFunc extends Expr {

    final Library.Function function;
    final boolean spread;
    final SsaValue[] args;

    protected CallFunc(Block belongTo, int pc, Library.Function function, boolean spread, SsaValue[] args) {
        super(belongTo, pc);
        this.function = function;
        this.spread = spread;
        this.args = args;
        for (SsaValue arg : args) {
            arg.addUsed(this);
        }
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

    public SsaValue[] getArgs() {
        return args;
    }
}
