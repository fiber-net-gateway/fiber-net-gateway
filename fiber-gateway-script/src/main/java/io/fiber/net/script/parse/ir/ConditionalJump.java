package io.fiber.net.script.parse.ir;

class ConditionalJump extends Instrument {
    private final Exp predict;
    private final CodeEnterPoint trueEnter;
    private final CodeEnterPoint falseEnter;

    ConditionalJump(Exp predict, CodeEnterPoint trueEnter, CodeEnterPoint falseEnter) {
        this.predict = predict;
        this.trueEnter = trueEnter;
        this.falseEnter = falseEnter;
    }


    public static ConditionalJump of(Exp predict, CodeEnterPoint trueEnter, CodeEnterPoint falseEnter) {
        return new ConditionalJump(predict, trueEnter, falseEnter);
    }

    public Exp getPredict() {
        return predict;
    }

    public CodeEnterPoint getTrueEnter() {
        return trueEnter;
    }

    public CodeEnterPoint getFalseEnter() {
        return falseEnter;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitConditionalJump(this);
    }
}
