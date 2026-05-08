package io.fiber.net.script.aot;

public final class SsaExceptionInput {
    private final SsaBlock catchBlock;
    private final VarState vars;
    private SsaValue exception;

    SsaExceptionInput(SsaBlock catchBlock, VarState vars, SsaValue exception) {
        this.catchBlock = catchBlock;
        this.vars = vars.copy();
        this.exception = exception;
    }

    public SsaBlock catchBlock() {
        return catchBlock;
    }

    public VarState vars() {
        return vars.copy();
    }

    public SsaValue exception() {
        return exception;
    }

    boolean replaceValue(SsaValue oldValue, SsaValue newValue) {
        boolean changed = vars.replace(oldValue, newValue);
        if (exception == oldValue) {
            exception = newValue;
            changed = true;
        }
        return changed;
    }
}
