package io.fiber.net.script.aot;

import io.fiber.net.script.Library;

public class CallConst extends Expr {

    final Library.Constant constant;

    protected CallConst(Block belongTo, int pc, Library.Constant constant) {
        super(belongTo, pc);
        this.constant = constant;
    }

    @Override
    public SsaValue.Type getResultType() {
        return SsaValue.Type.Unknown;
    }

    public Library.Constant getConstant() {
        return constant;
    }
}
