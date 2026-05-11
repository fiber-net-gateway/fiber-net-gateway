package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallAsyncFunc extends Expr {

    final Library.AsyncFunction function;
    final boolean spread;
    SsaValue[] args;

    protected CallAsyncFunc(Block belongTo, int pc, Library.AsyncFunction function, boolean spread, SsaValue[] args) {
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

    public Library.AsyncFunction getFunction() {
        return function;
    }

    public boolean isSpread() {
        return spread;
    }

    public SsaValue[] getArgs() {
        return args;
    }

    @Override
    public int replaceOperand(SsaValue oldVal, SsaValue newVal) {
        int replaced = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == oldVal) {
                args[i] = newVal;
                replaced++;
            }
        }
        return replaced;
    }
}
