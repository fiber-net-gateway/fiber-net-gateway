package io.fiber.net.script.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class ReflectFunction extends ReflectInvoker implements Library.Function {
    private final FunctionSignature signature;
    private final boolean constExpr;

    public ReflectFunction(Method method) {
        this(method, null);
    }

    public ReflectFunction(Method method, Object owner) {
        this(method, owner, method.getAnnotation(ScriptFunction.class));
    }

    ReflectFunction(Method method, Object owner, ScriptFunction function) {
        this(method, owner, function, function == null ? null : function.name());
    }

    ReflectFunction(Method method, Object owner, ScriptFunction function, String name) {
        super(method, owner, functionPlan(method));
        if (function == null) {
            throw invalid(method, "missing ScriptFunction");
        }
        checkFunction(method, false);
        this.signature = functionSignature(method, function, function.constExpr(), name);
        this.constExpr = function.constExpr();
    }

    @Override
    public FunctionSignature signature() {
        return signature;
    }

    @Override
    public boolean isConstExpr() {
        return constExpr;
    }

    @Override
    public JsonNode call(ExecutionContext context, Library.Arguments args) throws ScriptExecException {
        Object[] invokeArgs = fillArgs(context, args, null);
        try {
            return (JsonNode) invoke(invokeArgs);
        } catch (ScriptExecException e) {
            throw e;
        } catch (Throwable e) {
            throw ScriptExecException.fromThrowable(e);
        }
    }

    static void checkFunction(Method method, boolean async) {
        if (async) {
            if (method.getReturnType() != Void.TYPE) {
                throw invalid(method, "async function must return void");
            }
            checkAsyncExceptions(method);
        } else {
            if (method.getReturnType() != JsonNode.class) {
                throw invalid(method, "sync function must return JsonNode");
            }
            checkSyncExceptions(method);
        }
        parseFunction(method);
    }

    static FunctionSignature functionSignature(Method method, ScriptFunction function, boolean constExpr) {
        return functionSignature(method, function, constExpr, function.name());
    }

    static FunctionSignature functionSignature(Method method, ScriptFunction function, boolean constExpr, String name) {
        FuncLayout layout = parseFunction(method);
        ScriptParam[] declared = function.params();
        if (layout.arguments) {
            if (declared.length == 0) {
                return new FunctionSignature(name, constExpr, FunctionParam.variadic("args"));
            }
            return signature(name, constExpr, declared);
        }
        if (declared.length != 0) {
            validateDeclaredParams(method, layout, declared);
            return signature(name, constExpr, declared);
        }

        Annotation[][] annotations = method.getParameterAnnotations();
        FunctionParam[] params = new FunctionParam[layout.paramCount];
        int out = 0;
        for (int i = layout.firstScriptArg; i < method.getParameterTypes().length; i++) {
            Class<?> type = method.getParameterTypes()[i];
            ScriptParam param = findParam(annotations[i], method);
            if (type == JsonNode.class) {
                if (param.variadic()) {
                    throw invalid(method, "JsonNode parameter cannot be variadic");
                }
                params[out++] = toParam(param);
            } else {
                params[out++] = FunctionParam.variadic(param.value());
            }
        }
        return new FunctionSignature(name, constExpr, params);
    }

    static int[] functionPlan(Method method) {
        parseFunction(method);
        Class<?>[] types = method.getParameterTypes();
        int[] plan = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == ExecutionContext.class) {
                plan[i] = CTX;
            } else if (type == Library.AsyncHandle.class) {
                plan[i] = HANDLE;
            } else if (type == Library.Arguments.class) {
                plan[i] = ARGS;
            } else if (type == JsonNode.class) {
                plan[i] = ARG;
            } else {
                plan[i] = REST;
            }
        }
        return plan;
    }

    static boolean isAsync(Method method) {
        boolean found = false;
        for (Class<?> type : method.getParameterTypes()) {
            if (type == Library.AsyncHandle.class) {
                if (found) {
                    throw invalid(method, "duplicate AsyncHandle");
                }
                found = true;
            }
        }
        return found;
    }

    private static FuncLayout parseFunction(Method method) {
        Class<?>[] types = method.getParameterTypes();
        int i = 0;
        if (i < types.length && types[i] == ExecutionContext.class) {
            i++;
        }
        if (i < types.length && types[i] == Library.AsyncHandle.class) {
            i++;
        }
        int firstScriptArg = i;
        boolean arguments = false;
        boolean rest = false;
        int paramCount = 0;

        for (; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == ExecutionContext.class || type == Library.AsyncHandle.class) {
                throw invalid(method, "ExecutionContext and AsyncHandle must be before script arguments");
            }
            if (type == Library.Arguments.class) {
                if (arguments || rest || paramCount != 0 || i != types.length - 1) {
                    throw invalid(method, "Arguments must be the only script argument and last");
                }
                arguments = true;
                paramCount = 1;
            } else if (type == JsonNode.class) {
                if (arguments || rest) {
                    throw invalid(method, "JsonNode cannot follow Arguments or JsonNode[]");
                }
                paramCount++;
            } else if (type == JsonNode[].class) {
                if (arguments || rest || i != types.length - 1) {
                    throw invalid(method, "JsonNode[] must be last");
                }
                rest = true;
                paramCount++;
            } else {
                throw invalid(method, "unsupported function parameter");
            }
        }
        return new FuncLayout(firstScriptArg, paramCount, arguments);
    }

    private static ScriptParam findParam(Annotation[] annotations, Method method) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof ScriptParam) {
                return (ScriptParam) annotation;
            }
        }
        throw invalid(method, "JsonNode parameter requires ScriptParam");
    }

    private static void validateDeclaredParams(Method method, FuncLayout layout, ScriptParam[] declared) {
        if (declared.length != layout.paramCount) {
            throw invalid(method, "declared params count mismatch");
        }
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < declared.length; i++) {
            Class<?> type = types[layout.firstScriptArg + i];
            if (type == JsonNode[].class) {
                if (!declared[i].variadic()) {
                    throw invalid(method, "JsonNode[] requires variadic ScriptParam");
                }
            } else if (declared[i].variadic()) {
                throw invalid(method, "only JsonNode[] can use variadic ScriptParam");
            }
        }
    }

    private static class FuncLayout {
        final int firstScriptArg;
        final int paramCount;
        final boolean arguments;

        FuncLayout(int firstScriptArg, int paramCount, boolean arguments) {
            this.firstScriptArg = firstScriptArg;
            this.paramCount = paramCount;
            this.arguments = arguments;
        }
    }
}
