package io.fiber.net.script.lib;

import io.fiber.net.script.*;

import java.lang.reflect.Method;

public class ReflectAsyncFunction extends ReflectInvoker implements Library.AsyncFunction {
    private final FunctionSignature signature;

    public ReflectAsyncFunction(Method method) {
        this(method, null);
    }

    public ReflectAsyncFunction(Method method, Object owner) {
        this(method, owner, method.getAnnotation(ScriptFunction.class));
    }

    ReflectAsyncFunction(Method method, Object owner, ScriptFunction function) {
        super(method, owner, ReflectFunction.functionPlan(method));
        if (function == null) {
            throw invalid(method, "missing ScriptFunction");
        }
        ReflectFunction.checkFunction(method, true);
        this.signature = ReflectFunction.functionSignature(method, function, false);
    }

    @Override
    public FunctionSignature signature() {
        return signature;
    }

    @Override
    public void call(ExecutionContext context, Library.Arguments args, Library.AsyncHandle asyncHandle) {
        Object[] invokeArgs = fillArgs(context, args, asyncHandle);
        try {
            invoke(invokeArgs);
        } catch (Throwable e) {
            asyncHandle.throwErr(ScriptExecException.fromThrowable(e));
        }
    }
}
