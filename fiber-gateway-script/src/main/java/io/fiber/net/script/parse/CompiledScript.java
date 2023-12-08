package io.fiber.net.script.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.fiber.net.script.Script;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.Vm;


public class CompiledScript implements Script {
    private static final boolean errInfoIncludeSource = SystemPropertyUtil.getBoolean(
            "fiberNet.errorSource", false
    );

    public static CompiledScript create(String script, Node ast) throws ParseException {
        return createNonOptimise(script, OptimiserNodeVisitor.optimiseAst(ast));
    }

    public static CompiledScript createNonOptimise(String script, Node ast) throws ParseException {
        CompilerNodeVisitor.Compiled cpd = CompilerNodeVisitor.compile(ast);
        return new CompiledScript(script, cpd);
    }

    private final String expressionString;
    private final CompilerNodeVisitor.Compiled compiled;

    private CompiledScript(String expressionString, CompilerNodeVisitor.Compiled compiled) {
        this.expressionString = expressionString;
        this.compiled = compiled;
    }

    private static int startPos(long pos) {
        return ((int) (pos >> 16)) & 0xFFFF;
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        Vm vm = compiled.createVM(root, attach);
        Maybe<JsonNode> maybe = vm.exec();
        if (errInfoIncludeSource) {
            return maybe.onNotify((jsonNode, throwable) -> {
                if (throwable != null) {
                    String msg = "expression[" + expressionString + "]@" + startPos(compiled.getPos()[vm.getCurrentPc()]) +
                            ":" + throwable.getMessage();
                    int code = throwable instanceof FiberException ? ((FiberException) throwable).getCode() : 500;
                    String name = throwable instanceof FiberException ? ((FiberException) throwable).getErrorName() : "SCRIPT_ERROR";
                    throw new ScriptExecException(msg, throwable.getCause(), code, name);
                }
            });
        }
        return maybe;
    }
}
