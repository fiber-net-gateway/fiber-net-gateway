package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.InterpreterVm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;


public class CompiledScript implements Script {

    public static CompiledScript create(String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return create(script, block);
    }

    public static CompiledScript createNonOptimise(String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return createNonOptimise(script, block);
    }

    public static CompiledScript create(String script, Node ast) throws ParseException {
        return createNonOptimise(script, OptimiserNodeVisitor.optimiseAst(ast));
    }

    public static CompiledScript createNonOptimise(String script, Node ast) throws ParseException {
        Compiled cpd = CompilerNodeVisitor.compile(ast);
        return new CompiledScript(script, cpd);
    }

    private final String expressionString;
    private final Compiled compiled;
    private MethodHandle handle;

    private CompiledScript(String expressionString, Compiled compiled) {
        this.expressionString = expressionString;
        this.compiled = compiled;
        try {
            Class<?> aotClz = new AotClassGenerator(compiled).generateClz();
            MethodType type = MethodType.methodType(void.class, JsonNode.class, Object.class);
            handle = MethodHandles.lookup().findConstructor(aotClz, type);
        } catch (Throwable ignore) {
            handle = null;
        }
    }


    static InterpreterVm createVM(Compiled compiled, JsonNode root, Object attach) {
        return new InterpreterVm(root, attach, compiled);
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return createVM(compiled, root, attach).exec();
    }

    @Override
    public Maybe<JsonNode> aotExec(JsonNode root, Object attach) throws Exception {
        return createAotVm(root, attach).exec();
    }

    public InterpreterVm createInterpreterVm(JsonNode root, Object attach) {
        return createVM(compiled, root, attach);
    }

    public AbstractVm createAotVm(JsonNode root, Object attach) throws Exception {
        if (handle == null) {
            throw new IllegalStateException("aot compile failed");
        }
        try {
            return (AbstractVm) handle.invoke(root, attach);
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("cannot create aot vm", e);
        }
    }

    public String getExpressionString() {
        return expressionString;
    }

    public Compiled getCompiled() {
        return compiled;
    }
}
