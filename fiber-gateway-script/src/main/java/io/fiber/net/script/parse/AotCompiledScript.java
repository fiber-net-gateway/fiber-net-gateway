package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.aot.AotSsaCompiledScript;
import io.fiber.net.script.ast.Block;

public class AotCompiledScript implements Script {
    private static final String DEFAULT_SOURCE_FILE = "script.js";

    public static AotCompiledScript create(String script, Library library) throws ParseException {
        return create(DEFAULT_SOURCE_FILE, script, library);
    }

    public static AotCompiledScript create(String fileName, String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return create(fileName, script, block);
    }

    public static AotCompiledScript createNonOptimise(String script, Library library) throws ParseException {
        return createNonOptimise(DEFAULT_SOURCE_FILE, script, library);
    }

    public static AotCompiledScript createNonOptimise(String fileName, String script, Library library) throws ParseException {
        Parser parser = new Parser(library, true);
        Block block = parser.parseScript(script);
        return createNonOptimise(fileName, script, block);
    }

    public static AotCompiledScript create(Node ast) throws ParseException {
        return createNonOptimise(OptimiserNodeVisitor.optimiseAst(ast));
    }

    public static AotCompiledScript create(String fileName, String script, Node ast) throws ParseException {
        return createNonOptimise(fileName, script, OptimiserNodeVisitor.optimiseAst(ast));
    }

    public static AotCompiledScript createNonOptimise(Node ast) throws ParseException {
        Compiled cpd = CompilerNodeVisitor.compile(ast);
        return of(cpd);
    }

    public static AotCompiledScript createNonOptimise(String fileName, String script, Node ast) throws ParseException {
        Compiled cpd = CompilerNodeVisitor.compile(ast);
        cpd.setSourceInfo(fileName, Compiled.computeLineStartOffsets(script));
        return of(cpd);
    }

    public static AotCompiledScript of(Compiled compiled) throws ParseException {
        return new AotCompiledScript(AotSsaCompiledScript.of(compiled));
    }

    private final Script delegate;

    private AotCompiledScript(Script delegate) {
        this.delegate = delegate;
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return delegate.exec(root, attach);
    }

    @Override
    public boolean containsAsyncIR() {
        return delegate.containsAsyncIR();
    }

    @Override
    public JsonNode execForSync(JsonNode root, Object attach) throws Throwable {
        return delegate.execForSync(root, attach);
    }
}
