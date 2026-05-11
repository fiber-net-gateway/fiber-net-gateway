package io.fiber.net.script.aot;


public abstract class Expr extends Instruction {


    private SsaValue result;

    protected Expr(Block belongTo, int pc) {
        super(belongTo, pc);
        this.result = new SsaValue(this);
    }

    public void setResult(SsaValue result) {
        this.result = result;
    }

    public SsaValue getResult() {
        return result;
    }

    public abstract SsaValue.Type getResultType();
}
