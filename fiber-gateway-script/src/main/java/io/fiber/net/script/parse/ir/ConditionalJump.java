package io.fiber.net.script.parse.ir;

class ConditionalJump extends Instrument {
    private final Exp predict;
    private final CodeEnterPoint target;
    private final boolean trueJump;
    private boolean optimiseIf;


    ConditionalJump(Exp predict, CodeEnterPoint target, boolean trueJump) {
        this.predict = predict;
        this.target = target;
        this.trueJump = trueJump;
    }


    public static ConditionalJump of(Exp predict, CodeEnterPoint target, boolean trueJump) {
        return new ConditionalJump(predict, target, trueJump);
    }

    public Exp getPredict() {
        return predict;
    }

    public CodeEnterPoint getTarget() {
        return target;
    }

    public boolean isTrueJump() {
        return trueJump;
    }

    public void setOptimiseIf() {
        this.optimiseIf = true;
    }

    public boolean isOptimiseIf() {
        return optimiseIf;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitConditionalJump(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.conditionalJump(this);
        return -1;
    }
}
