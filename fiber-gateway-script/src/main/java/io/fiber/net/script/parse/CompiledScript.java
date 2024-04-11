package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.InterpreterVm;


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
    private Class<?> aotClz;

    private CompiledScript(String expressionString, Compiled compiled) {
        this.expressionString = expressionString;
        this.compiled = compiled;
        try {
            aotClz = new AotClassGenerator(compiled).generateClz();
        } catch (Throwable e) {
            aotClz = null;
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
        if (aotClz == null) {
            throw new IllegalStateException("aot compile failed");
        }
        return (AbstractVm) aotClz.getConstructor(JsonNode.class, Object.class).newInstance(root, attach);
    }

    public String getExpressionString() {
        return expressionString;
    }

    public Compiled getCompiled() {
        return compiled;
    }
}
