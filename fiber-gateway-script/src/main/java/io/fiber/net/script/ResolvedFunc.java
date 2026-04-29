package io.fiber.net.script;

public final class ResolvedFunc {
    private final FunctionSignature signature;
    private final Library.Function function;
    private final Library.AsyncFunction asyncFunction;

    private ResolvedFunc(FunctionSignature signature, Library.Function function, Library.AsyncFunction asyncFunction) {
        this.signature = signature;
        this.function = function;
        this.asyncFunction = asyncFunction;
    }

    public static ResolvedFunc sync(FunctionSignature signature, Library.Function function) {
        return new ResolvedFunc(signature, function, null);
    }

    public static ResolvedFunc async(FunctionSignature signature, Library.AsyncFunction function) {
        return new ResolvedFunc(signature, null, function);
    }

    public FunctionSignature getSignature() {
        return signature;
    }

    public Library.Function getFunction() {
        return function;
    }

    public Library.AsyncFunction getAsyncFunction() {
        return asyncFunction;
    }

    public boolean isAsync() {
        return asyncFunction != null;
    }
}
