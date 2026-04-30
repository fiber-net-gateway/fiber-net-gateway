package io.fiber.net.script.lib;

import io.fiber.net.script.std.StdLibrary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ReflectLib {
    private static final Pattern FUNCTION_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*(\\.[\\p{L}_][\\p{L}\\p{N}_]*)*");
    private static final Pattern CONSTANT_NAMESPACE = Pattern.compile("\\$[\\p{L}_][\\p{L}\\p{N}_]*");
    private static final Pattern CONSTANT_KEY = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

    private ReflectLib() {
    }

    public static void register(StdLibrary library, Object owner) {
        Objects.requireNonNull(owner, "owner");
        register(library, owner.getClass(), owner);
    }

    public static void registerStatic(StdLibrary library, Class<?> type) {
        register(library, type, null);
    }

    private static void register(StdLibrary library, Class<?> type, Object owner) {
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(type, "type");
        String defaultNamespace = "";
        String functionPrefix = "";
        ScriptLib scriptLib = type.getAnnotation(ScriptLib.class);
        if (scriptLib != null) {
            defaultNamespace = scriptLib.namespace();
            functionPrefix = scriptLib.functionPrefix();
            if (functionPrefix.length() != 0) {
                validateFunctionName(functionPrefix, methodSource(type), "function prefix");
            }
        }

        for (Method method : type.getMethods()) {
            ScriptFunction function = method.getAnnotation(ScriptFunction.class);
            ScriptConstant constant = method.getAnnotation(ScriptConstant.class);
            if (function != null && constant != null) {
                throw ReflectInvoker.invalid(method, "method cannot be function and constant");
            }
            if (owner == null && !Modifier.isStatic(method.getModifiers())
                    && (function != null || constant != null)) {
                throw ReflectInvoker.invalid(method, "static registration requires static methods");
            }
            if (function != null) {
                String name = functionName(functionPrefix, function.name(), method);
                if (ReflectFunction.isAsync(method)) {
                    library.putAsyncFunc(name, new ReflectAsyncFunction(method, owner, function, name));
                } else {
                    library.putFunc(name, new ReflectFunction(method, owner, function, name));
                }
            } else if (constant != null) {
                String namespace = constant.namespace();
                if (namespace.length() == 0) {
                    namespace = defaultNamespace;
                }
                validateConstant(namespace, constant.key(), method);
                if (ReflectConstant.isAsync(method)) {
                    library.putAsyncConstant(namespace, constant.key(), new ReflectAsyncConstant(method, owner));
                } else {
                    library.putConstant(namespace, constant.key(), new ReflectConstant(method, owner));
                }
            }
        }
    }

    private static String functionName(String prefix, String name, Method method) {
        validateFunctionName(name, method, "function name");
        if (prefix.length() == 0) {
            return name;
        }
        String fullName = prefix + '.' + name;
        validateFunctionName(fullName, method, "function name");
        return fullName;
    }

    private static void validateFunctionName(String value, Method method, String label) {
        if (!FUNCTION_NAME.matcher(value).matches()) {
            throw ReflectInvoker.invalid(method, "invalid " + label + ": " + value);
        }
    }

    private static void validateFunctionName(String value, String source, String label) {
        if (!FUNCTION_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + label + ": " + value + ": " + source);
        }
    }

    private static void validateConstant(String namespace, String key, Method method) {
        if (!CONSTANT_NAMESPACE.matcher(namespace).matches()) {
            throw ReflectInvoker.invalid(method, "constant namespace must start with $ and be a valid identifier: " + namespace);
        }
        if (!CONSTANT_KEY.matcher(key).matches()) {
            throw ReflectInvoker.invalid(method, "invalid constant key: " + key);
        }
    }

    private static String methodSource(Class<?> type) {
        return type.getName();
    }
}
