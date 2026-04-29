package io.fiber.net.script.lib;

import io.fiber.net.script.std.StdLibrary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public final class ReflectLib {
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
        ScriptLib scriptLib = type.getAnnotation(ScriptLib.class);
        if (scriptLib != null) {
            defaultNamespace = scriptLib.namespace();
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
                if (ReflectFunction.isAsync(method)) {
                    library.putAsyncFunc(function.name(), new ReflectAsyncFunction(method, owner));
                } else {
                    library.putFunc(function.name(), new ReflectFunction(method, owner));
                }
            } else if (constant != null) {
                String namespace = constant.namespace();
                if (namespace.length() == 0) {
                    namespace = defaultNamespace;
                }
                if (namespace.length() == 0) {
                    throw ReflectInvoker.invalid(method, "constant namespace is empty");
                }
                if (ReflectConstant.isAsync(method)) {
                    library.putAsyncConstant(namespace, constant.key(), new ReflectAsyncConstant(method, owner));
                } else {
                    library.putConstant(namespace, constant.key(), new ReflectConstant(method, owner));
                }
            }
        }
    }
}
