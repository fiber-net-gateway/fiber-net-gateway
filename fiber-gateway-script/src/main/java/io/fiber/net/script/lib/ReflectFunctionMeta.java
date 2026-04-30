package io.fiber.net.script.lib;

import io.fiber.net.script.FunctionSignature;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

final class ReflectFunctionMeta {
    final Method method;
    final MethodHandle handle;
    final int[] argPlan;
    final FunctionSignature signature;
    final boolean async;
    final boolean constExpr;

    ReflectFunctionMeta(Method method, ScriptFunction function) {
        if (function == null) {
            throw ReflectInvoker.invalid(method, "missing ScriptFunction");
        }
        async = ReflectFunction.isAsync(method);
        ReflectFunction.checkFunction(method, async);
        this.method = method;
        handle = ReflectInvoker.unreflect(method);
        argPlan = ReflectFunction.functionPlan(method);
        constExpr = !async && function.constExpr();
        signature = ReflectFunction.functionSignature(method, function, constExpr);
    }

    FunctionSignature signature(String name) {
        return new FunctionSignature(name, signature.isConstExpr(), signature.getParams());
    }
}
