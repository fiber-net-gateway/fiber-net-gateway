package io.fiber.net.script.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.lang.reflect.Method;

public class ReflectConstant extends ReflectInvoker implements Library.Constant {
    private final boolean constExpr;

    public ReflectConstant(Method method) {
        this(method, null);
    }

    public ReflectConstant(Method method, Object owner) {
        this(method, owner, method.getAnnotation(ScriptConstant.class));
    }

    ReflectConstant(Method method, Object owner, ScriptConstant constant) {
        super(method, owner, constantPlan(method));
        if (constant == null) {
            throw invalid(method, "missing ScriptConstant");
        }
        checkConstant(method, false);
        this.constExpr = constant.constExpr();
    }

    @Override
    public boolean isConstExpr() {
        return constExpr;
    }

    @Override
    public JsonNode get(ExecutionContext context) throws ScriptExecException {
        Object[] invokeArgs = fillArgs(context, null, null);
        try {
            return (JsonNode) invoke(invokeArgs);
        } catch (ScriptExecException e) {
            throw e;
        } catch (Throwable e) {
            throw ScriptExecException.fromThrowable(e);
        }
    }

    static void checkConstant(Method method, boolean async) {
        if (async) {
            if (method.getReturnType() != Void.TYPE) {
                throw invalid(method, "async constant must return void");
            }
            checkAsyncExceptions(method);
        } else {
            if (method.getReturnType() != JsonNode.class) {
                throw invalid(method, "sync constant must return JsonNode");
            }
            checkSyncExceptions(method);
        }
        parseConstant(method);
    }

    static int[] constantPlan(Method method) {
        parseConstant(method);
        Class<?>[] types = method.getParameterTypes();
        int[] plan = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == ExecutionContext.class) {
                plan[i] = CTX;
            } else {
                plan[i] = HANDLE;
            }
        }
        return plan;
    }

    static boolean isAsync(Method method) {
        return ReflectFunction.isAsync(method);
    }

    private static void parseConstant(Method method) {
        Class<?>[] types = method.getParameterTypes();
        int i = 0;
        if (i < types.length && types[i] == ExecutionContext.class) {
            i++;
        }
        if (i < types.length && types[i] == Library.AsyncHandle.class) {
            i++;
        }
        if (i != types.length) {
            throw invalid(method, "constant only allows ExecutionContext and AsyncHandle");
        }
    }
}
