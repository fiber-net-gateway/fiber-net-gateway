package io.fiber.net.script.parse.ir;

class FunctionCall extends Exp {
    private final int funcId;
    private final Exp[] args;
    private final Exp spreadArgs;
    private final boolean async;


    FunctionCall(int funcId, Exp[] args, Exp spreadArgs, boolean async) {
        this.funcId = funcId;
        this.args = args;
        this.spreadArgs = spreadArgs;
        this.async = async;
    }

    int getArgCount() {
        return args.length;
    }

    int getFuncId() {
        return funcId;
    }

    boolean isSpread() {
        return spreadArgs != null;
    }

    static FunctionCall of(int funcId, Exp[] args, boolean async) {
        return new FunctionCall(funcId, args, null, async);
    }

    static FunctionCall spread(int funcId, Exp exp, boolean async) {
        return new FunctionCall(funcId, null, exp, async);
    }

    boolean isAsync() {
        return async;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitFunctionCall(this);
    }
}
