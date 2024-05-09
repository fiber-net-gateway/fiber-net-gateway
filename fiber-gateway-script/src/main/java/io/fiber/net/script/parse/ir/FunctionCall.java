package io.fiber.net.script.parse.ir;

class FunctionCall extends Exp {
    private final int funcId;
    private final Exp[] args;
    private final Exp spreadArgs;
    private final int asyncPoint;
    private int restoreStackSize;
    private int stashStackSize;

    FunctionCall(int funcId, Exp[] args, Exp spreadArgs, int asyncPoint) {
        this.funcId = funcId;
        this.args = args;
        this.spreadArgs = spreadArgs;
        this.asyncPoint = asyncPoint;
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

    void setRestoreStackSize(int restoreStackSize) {
        this.restoreStackSize = restoreStackSize;
    }

    int getRestoreStackSize() {
        return restoreStackSize;
    }

    int getStashStackSize() {
        return stashStackSize;
    }

    void setStashStackSize(int stashStackSize) {
        this.stashStackSize = stashStackSize;
    }

    static FunctionCall of(int funcId, Exp[] args, int asyncPoint) {
        return new FunctionCall(funcId, args, null, asyncPoint);
    }

    static FunctionCall spread(int funcId, Exp exp, int asyncPoint) {
        return new FunctionCall(funcId, null, exp, asyncPoint);
    }

    boolean isAsync() {
        return asyncPoint >= 0;
    }

    int getAsyncPoint() {
        return asyncPoint;
    }

    @Override
    int assemble(ClzAssembler assembler) {
        for (int i = getStashStackSize() - 1; i >= 0; i--) {
            assembler.stashStack(i);
        }
        if (isAsync()) {
            assembler.asyncFuncCall(this);
        } else {
            assembler.syncFuncCall(this);
        }
        return isSpread() ? 0 : -args.length + 1;
    }

}
