package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallFunc extends Expr {

    final Library.Function function;
    final boolean spread;
    SsaValue[] args;

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

    @Override
    void dropOperands() {
        for (SsaValue arg : args) {
            arg.removeUsed(this);
        }
    }

    @Override
    public int effects() {
        return EFFECT_CALL | EFFECT_MEMORY_WRITE;
    }
}
