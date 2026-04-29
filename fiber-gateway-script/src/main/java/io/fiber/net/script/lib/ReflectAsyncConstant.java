package io.fiber.net.script.lib;

import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.lang.reflect.Method;

public class ReflectAsyncConstant extends ReflectInvoker implements Library.AsyncConstant {

    public ReflectAsyncConstant(Method method) {
        this(method, null);
    }

    public ReflectAsyncConstant(Method method, Object owner) {
        this(method, owner, method.getAnnotation(ScriptConstant.class));
    }

    ReflectAsyncConstant(Method method, Object owner, ScriptConstant constant) {
        super(method, owner, ReflectConstant.constantPlan(method));
        if (constant == null) {
            throw invalid(method, "missing ScriptConstant");
        }
        ReflectConstant.checkConstant(method, true);
    }

    @Override
    public void get(ExecutionContext context, Library.AsyncHandle asyncHandle) {
        Object[] invokeArgs = fillArgs(context, null, asyncHandle);
        try {
            invoke(invokeArgs);
        } catch (Throwable e) {
            asyncHandle.throwErr(ScriptExecException.fromThrowable(e));
        }
    }
}
