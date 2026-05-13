package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallAsyncConst extends Expr {

    final Library.AsyncConstant constant;

    protected CallAsyncConst(Block belongTo, int pc, Library.AsyncConstant constant) {
        super(belongTo, pc);
        this.constant = constant;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }

    public Library.AsyncConstant getConstant() {
        return constant;
    }

    @Override
    public int effects() {
        return EFFECT_CALL | EFFECT_MEMORY_WRITE;
    }
}
