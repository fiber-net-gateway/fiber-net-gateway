package io.fiber.net.script.std;

import io.fiber.net.script.*;
import io.fiber.net.script.ast.Literal;

import java.util.*;

public class StdLibrary implements Library {
    private static final StdLibrary DEF_INSTANCE = new StdLibrary();

    public static StdLibrary getDefInstance() {
        return DEF_INSTANCE;
    }

    protected final Map<String, List<ResolvedFunc>> functionMap = new HashMap<>();
    protected final Map<String, Object> constantMap = new HashMap<>();

    public StdLibrary() {
    }

    public void putFunc(Function function) {
        FunctionSignature signature = Objects.requireNonNull(function.signature(), "function signature");
        register(ResolvedFunc.sync(signature, function));
    }

    public void putFunc(String name, Function function) {
        FunctionSignature signature = function.signature();
        if (signature == null) {
            signature = new FunctionSignature(name, function.isConstExpr(), FunctionParam.variadic("args"));
        }
        putFunc(name, signature, function);
    }

    public void putFunc(String name, FunctionSignature signature, Function function) {
        checkName(name, signature);
        register(ResolvedFunc.sync(signature, Objects.requireNonNull(function)));
    }

    public void putAsyncFunc(AsyncFunction function) {
        FunctionSignature signature = Objects.requireNonNull(function.signature(), "async function signature");
        register(ResolvedFunc.async(signature, function));
    }

    public void putAsyncFunc(String name, AsyncFunction function) {
        FunctionSignature signature = function.signature();
        if (signature == null) {
            signature = new FunctionSignature(name, false, FunctionParam.variadic("args"));
        }
        putAsyncFunc(name, signature, function);
    }

    public void putAsyncFunc(String name, FunctionSignature signature, AsyncFunction function) {
        checkName(name, signature);
        register(ResolvedFunc.async(signature, Objects.requireNonNull(function)));
    }

    public void putConstant(String namespace, String key, Constant constant) {
        String name = constantName(namespace, key);
        Object old = constantMap.put(name, Objects.requireNonNull(constant));
        if (old != null) {
            throw new IllegalStateException("constant " + name + " exists");
        }
    }

    public void putAsyncConstant(String namespace, String key, AsyncConstant constant) {
        String name = constantName(namespace, key);
        Object old = constantMap.put(name, Objects.requireNonNull(constant));
        if (old != null) {
            throw new IllegalStateException("constant " + name + " exists");
        }
    }

    private void register(ResolvedFunc func) {
        FunctionSignature signature = func.getSignature();
        List<ResolvedFunc> funcs = functionMap.computeIfAbsent(signature.getName(), k -> new ArrayList<>());
        for (ResolvedFunc old : funcs) {
            if (old.getSignature().overlaps(signature)) {
                throw new IllegalStateException("function signature conflicts: "
                        + old.getSignature().display() + " / " + signature.display());
            }
        }
        funcs.add(func);
    }

    private static void checkName(String name, FunctionSignature signature) {
        Objects.requireNonNull(signature, "function signature");
        if (!Objects.equals(name, signature.getName())) {
            throw new IllegalArgumentException("function name mismatch: " + name + " / " + signature.getName());
        }
    }

    private static String constantName(String namespace, String key) {
        return namespace + "/" + key;
    }

    @Override
    public ResolvedFunc resolveFunc(String name, FunctionCallArgs args) {
        List<ResolvedFunc> funcs = functionMap.get(name);
        if (funcs == null) {
            return null;
        }
        ResolvedFunc matched = null;
        for (ResolvedFunc func : funcs) {
            if (func.getSignature().matches(args)) {
                if (matched != null) {
                    throw new IllegalStateException("ambiguous function call: " + name);
                }
                matched = func;
            }
        }
        return matched;
    }

    @Override
    public Object findConstant(String namespace, String key) {
        return constantMap.get(constantName(namespace, key));
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        return null;
    }
}
