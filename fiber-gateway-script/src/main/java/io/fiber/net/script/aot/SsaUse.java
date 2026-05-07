package io.fiber.net.script.aot;

public final class SsaUse {
    private final SsaValue value;
    private final SsaValue user;
    private final int operandIndex;

    SsaUse(SsaValue value, SsaValue user, int operandIndex) {
        this.value = value;
        this.user = user;
        this.operandIndex = operandIndex;
    }

    public SsaValue value() {
        return value;
    }

    public SsaValue user() {
        return user;
    }

    public int operandIndex() {
        return operandIndex;
    }

    @Override
    public String toString() {
        return value + " used by " + user + '[' + operandIndex + ']';
    }
}
