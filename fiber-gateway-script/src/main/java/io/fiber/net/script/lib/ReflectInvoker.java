package io.fiber.net.script.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.*;
import io.fiber.net.script.run.AbstractVm;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

abstract class ReflectInvoker implements DirectReflectInvoker {
    static final int CTX = 1;
    static final int HANDLE = 2;
    static final int ARGS = 3;
    static final int ARG = 4;
    static final int REST = 5;

    final Method method;
    final Object owner;
    final MethodHandle handle;
    final int[] argPlan;

    ReflectInvoker(Method method, Object owner, int[] argPlan) {
        checkBase(method, owner);
        this.method = method;
        this.owner = owner;
        this.argPlan = argPlan;
        this.handle = unreflect(method, owner);
    }

    @Override
    public final Method directMethod() {
        return method;
    }

    @Override
    public final Object directOwner() {
        return owner;
    }

    @Override
    public final int[] directArgPlan() {
        return argPlan.clone();
    }

    Object invoke(Object[] args) throws Throwable {
        switch (args.length) {
            case 0:
                return handle.invoke();
            case 1:
                return handle.invoke(args[0]);
            case 2:
                return handle.invoke(args[0], args[1]);
            case 3:
                return handle.invoke(args[0], args[1], args[2]);
            case 4:
                return handle.invoke(args[0], args[1], args[2], args[3]);
            case 5:
                return handle.invoke(args[0], args[1], args[2], args[3], args[4]);
            case 6:
                return handle.invoke(args[0], args[1], args[2], args[3], args[4], args[5]);
            case 7:
                return handle.invoke(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
            case 8:
                return handle.invoke(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
            default:
                return handle.invokeWithArguments(args);
        }
    }

    Object[] fillArgs(ExecutionContext context, Library.Arguments args, Library.AsyncHandle asyncHandle) {
        Object[] invokeArgs = new Object[argPlan.length];
        int[] plan = this.argPlan;
        int argIndex = 0;
        for (int i = 0; i < plan.length; i++) {
            switch (plan[i]) {
                case CTX:
                    invokeArgs[i] = context;
                    break;
                case HANDLE:
                    invokeArgs[i] = asyncHandle;
                    break;
                case ARGS:
                    invokeArgs[i] = args;
                    break;
                case ARG:
                    invokeArgs[i] = args.getArgVal(argIndex++);
                    break;
                case REST:
                    invokeArgs[i] = restArgs(args, argIndex);
                    break;
                default:
                    throw new IllegalStateException("unknown argument plan");
            }
        }
        return invokeArgs;
    }

    static void checkBase(Method method, Object owner) {
        Class<?> declaring = method.getDeclaringClass();
        if (!Modifier.isPublic(method.getModifiers())) {
            throw invalid(method, "method must be public");
        }
        if (!Modifier.isPublic(declaring.getModifiers())) {
            throw invalid(method, "declaring class must be public");
        }
        if (declaring.getClassLoader() != AbstractVm.class.getClassLoader()) {
            throw invalid(method, "declaring class must use AbstractVm classLoader");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            return;
        }
        if (owner == null) {
            throw invalid(method, "instance method requires owner");
        }
        if (!declaring.isInstance(owner)) {
            throw invalid(method, "owner is not declaring class instance");
        }
    }

    static void checkSyncExceptions(Method method) {
        Class<?>[] types = method.getExceptionTypes();
        for (Class<?> type : types) {
            if (!ScriptExecException.class.isAssignableFrom(type)) {
                throw invalid(method, "sync method can only throw ScriptExecException");
            }
        }
    }

    static void checkAsyncExceptions(Method method) {
        if (method.getExceptionTypes().length != 0) {
            throw invalid(method, "async method cannot declare exceptions");
        }
    }

    static FunctionSignature signature(String name, boolean constExpr, ScriptParam[] params) {
        FunctionParam[] functionParams = new FunctionParam[params.length];
        for (int i = 0; i < params.length; i++) {
            ScriptParam param = params[i];
            functionParams[i] = toParam(param);
        }
        return new FunctionSignature(name, constExpr, functionParams);
    }

    static FunctionParam toParam(ScriptParam param) {
        if (param.variadic()) {
            return FunctionParam.variadic(param.value());
        }
        if (param.optional()) {
            return FunctionParam.optional(param.value(), defaultValue(param));
        }
        return FunctionParam.required(param.value());
    }

    static JsonNode defaultValue(ScriptParam param) {
        try {
            return JsonUtil.readTree(param.defaultValue());
        } catch (IOException e) {
            throw new IllegalArgumentException("bad default value: " + param.value(), e);
        }
    }

    static IllegalArgumentException invalid(Method method, String msg) {
        return new IllegalArgumentException(msg + ": " + method);
    }

    private static MethodHandle unreflect(Method method, Object owner) {
        try {
            MethodHandle mh = MethodHandles.lookup().unreflect(method);
            if (method.isVarArgs()) {
                mh = mh.asFixedArity();
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                mh = mh.bindTo(owner);
            }
            return mh;
        } catch (IllegalAccessException e) {
            throw invalid(method, "cannot access method");
        }
    }

    private static JsonNode[] restArgs(Library.Arguments args, int off) {
        int count = args.getArgCnt() - off;
        if (count <= 0) {
            return new JsonNode[0];
        }
        JsonNode[] nodes = new JsonNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = args.getArgVal(off + i);
        }
        return nodes;
    }
}
