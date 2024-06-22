package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.run.InterpreterVm;


public class InterpretorScript implements Script {
    public static InterpretorScript create(String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return create(script, block);
    }

    public static InterpretorScript createNonOptimise(String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return createNonOptimise(script, block);
    }

    public static InterpretorScript create(String script, Node ast) throws ParseException {
        return createNonOptimise(script, OptimiserNodeVisitor.optimiseAst(ast));
    }

    public static InterpretorScript createNonOptimise(String script, Node ast) throws ParseException {
        Compiled cpd = CompilerNodeVisitor.compile(ast);
        return new InterpretorScript(script, cpd);
    }

    private final String expressionString;
    private final Compiled compiled;

    private InterpretorScript(String expressionString, Compiled compiled) {
        this.expressionString = expressionString;
        this.compiled = compiled;
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return Maybe.create(emitter -> createInterpreterVm(root, attach, emitter).exec());
    }

    public InterpreterVm createInterpreterVm(JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) {
        return new InterpreterVm(compiled, root, attach, resultEmitter);
    }

    public String getExpressionString() {
        return expressionString;
    }

    public Compiled getCompiled() {
        return compiled;
    }

    public AotCompiledScript createAotCompiledScript() throws ParseException {
        return AotCompiledScript.of(compiled);
    }
}
