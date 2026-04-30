package io.fiber.net.script.lib;

import io.fiber.net.script.FunctionCallArgs;
import io.fiber.net.script.FunctionSignature;
import io.fiber.net.script.Library;
import io.fiber.net.script.ResolvedFunc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ReflectDirective implements Library.DirectiveDef {
    private static final Pattern FUNCTION_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");
    private static final ConcurrentHashMap<Class<?>, DirectiveMeta> CACHE = new ConcurrentHashMap<>();

    private final Object owner;
    private final DirectiveMeta meta;
    private final Map<ResolvedKey, ResolvedFunc> resolvedCache = new HashMap<>();

    private ReflectDirective(Object owner, DirectiveMeta meta) {
        this.owner = owner;
        this.meta = meta;
    }

    public static ReflectDirective of(Object owner) {
        Objects.requireNonNull(owner, "owner");
        Class<?> type = owner.getClass();
        DirectiveMeta meta = CACHE.computeIfAbsent(type, DirectiveMeta::new);
        return new ReflectDirective(owner, meta);
    }

    public String type() {
        return meta.type;
    }

    @Override
    public ResolvedFunc resolveFunc(String directive, String function, FunctionCallArgs args) {
        List<ReflectFunctionMeta> candidates = meta.functions.get(function);
        if (candidates == null) {
            return null;
        }
        String resolvedName = directive + '.' + function;
        ResolvedFunc matched = null;
        for (int i = 0; i < candidates.size(); i++) {
            ReflectFunctionMeta functionMeta = candidates.get(i);
            FunctionSignature signature = functionMeta.signature(resolvedName);
            if (!signature.matches(args)) {
                continue;
            }
            if (matched != null) {
                throw new IllegalStateException("ambiguous directive function call: " + resolvedName);
            }
            matched = resolvedFunc(new ResolvedKey(resolvedName, i), functionMeta, signature);
        }
        return matched;
    }

    private ResolvedFunc resolvedFunc(ResolvedKey key, ReflectFunctionMeta meta, FunctionSignature signature) {
        ResolvedFunc resolved = resolvedCache.get(key);
        if (resolved != null) {
            return resolved;
        }
        if (meta.async) {
            resolved = ResolvedFunc.async(signature, new ReflectAsyncFunction(meta, owner, signature));
        } else {
            resolved = ResolvedFunc.sync(signature, new ReflectFunction(meta, owner, signature));
        }
        resolvedCache.put(key, resolved);
        return resolved;
    }

    private static final class DirectiveMeta {
        final String type;
        final Map<String, List<ReflectFunctionMeta>> functions = new HashMap<>();

        DirectiveMeta(Class<?> ownerType) {
            ScriptDirective directive = ownerType.getAnnotation(ScriptDirective.class);
            if (directive == null) {
                throw new IllegalArgumentException("missing ScriptDirective: " + ownerType.getName());
            }
            ReflectInvoker.checkMethodBase(ownerType);
            type = directive.value();

            for (Method method : ownerType.getMethods()) {
                ScriptFunction function = method.getAnnotation(ScriptFunction.class);
                if (function == null) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    throw ReflectInvoker.invalid(method, "directive function cannot be static");
                }
                String name = function.name();
                if (!FUNCTION_NAME.matcher(name).matches()) {
                    throw ReflectInvoker.invalid(method, "invalid directive function name: " + name);
                }
                add(name, new ReflectFunctionMeta(method, function));
            }
        }

        private void add(String name, ReflectFunctionMeta meta) {
            List<ReflectFunctionMeta> metas = functions.computeIfAbsent(name, k -> new ArrayList<>());
            for (ReflectFunctionMeta old : metas) {
                if (old.signature.overlaps(meta.signature)) {
                    throw new IllegalStateException("directive function signature conflicts: "
                            + old.signature.display() + " / " + meta.signature.display());
                }
            }
            metas.add(meta);
        }
    }

    private static final class ResolvedKey {
        final String name;
        final int index;

        ResolvedKey(String name, int index) {
            this.name = name;
            this.index = index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ResolvedKey)) {
                return false;
            }
            ResolvedKey that = (ResolvedKey) o;
            return index == that.index && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + index;
        }
    }
}
