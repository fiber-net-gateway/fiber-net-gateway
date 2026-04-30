package io.fiber.net.proxy.lib;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.script.FunctionCallArgs;
import io.fiber.net.script.FunctionSignature;
import io.fiber.net.script.Library;
import io.fiber.net.script.ResolvedFunc;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.std.StdLibrary;

import java.util.List;

public class ExtensiveHttpLib extends StdLibrary {
    final Injector injector;
    final HttpLibConfigure[] configures;

    public ExtensiveHttpLib() {
        this.injector = null;
        this.configures = null;
    }

    public ExtensiveHttpLib(Injector injector) {
        this(injector, injector.getInstances(HttpLibConfigure.class));
    }

    public ExtensiveHttpLib(Injector injector, HttpLibConfigure[] configures) {
        this.injector = injector;
        this.configures = configures;

        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                configure.onInit(this);
            }
        }
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                DirectiveDef def = configure.findDirectiveDef(type, name, literals);
                if (def != null) {
                    return def;
                }
            }
        }
        return null;
    }

    @Override
    public Object findConstant(String namespace, String key) {
        Object constant = super.findConstant(namespace, key);
        if (constant != null) {
            return constant;
        }
        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                Library.Constant c = configure.findConstant(namespace, key);
                if (c != null) {
                    return c;
                }

                Library.AsyncConstant ac = configure.findAsyncConstant(namespace, key);
                if (ac != null) {
                    return ac;
                }
            }
        }
        return null;
    }

    @Override
    public ResolvedFunc resolveFunc(String name, FunctionCallArgs args) {
        ResolvedFunc func = super.resolveFunc(name, args);
        if (func != null) {
            return func;
        }
        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                Library.Function c = configure.findFunction(name);
                ResolvedFunc resolved = resolveSync(name, args, c);
                if (resolved != null) {
                    return resolved;
                }

                Library.AsyncFunction ac = configure.findAsyncFunction(name);
                resolved = resolveAsync(name, args, ac);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static ResolvedFunc resolveSync(String name, FunctionCallArgs args, Library.Function function) {
        if (function == null) {
            return null;
        }
        FunctionSignature signature = function.signature();
        if (signature == null) {
            return null;
        }
        if (!signature.matches(args)) {
            return null;
        }
        return ResolvedFunc.sync(signature, function);
    }

    private static ResolvedFunc resolveAsync(String name, FunctionCallArgs args, Library.AsyncFunction function) {
        if (function == null) {
            return null;
        }
        FunctionSignature signature = function.signature();
        if (signature == null) {
            return null;
        }
        if (!signature.matches(args)) {
            return null;
        }
        return ResolvedFunc.async(signature, function);
    }
}
