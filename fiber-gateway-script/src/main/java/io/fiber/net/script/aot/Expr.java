package io.fiber.net.script.aot;


public abstract class Expr extends Instruction {


    final SsaValue result;

    protected Expr(Block belongTo, int pc) {
        super(belongTo, pc);
        this.result = new SsaValue(this);
    }

    public SsaValue getResult() {
        return result;
    }

    public abstract SsaValue.Type getResultType();
}
