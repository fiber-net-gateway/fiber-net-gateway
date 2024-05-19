package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.InterpreterVm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;


public class CompiledScript implements Script {
    private static final String ERROR_PATH = SystemPropertyUtil.get("fiber.aotErrorClzDumpPath", "/tmp/fiber_err_clz");

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

    private static final Logger log = LoggerFactory.getLogger(CompiledScript.class);
    private static final MethodType METHOD_TYPE = MethodType.methodType(void.class, JsonNode.class, Object.class, Maybe.Emitter.class);

    private final String expressionString;
    private final Compiled compiled;
    private MethodHandle handle;

    private CompiledScript(String expressionString, Compiled compiled) {
        this.expressionString = expressionString;
        this.compiled = compiled;
        AotClassGenerator generator = new AotClassGenerator(compiled);
        try {
            Class<?> aotClz = generator.generateClz();
            handle = MethodHandles.lookup().findConstructor(aotClz, METHOD_TYPE);
        } catch (Throwable e) {
            String clzName = generator.getClzName();
            log.error("error create aot VM: clz->{}", clzName, e);
            int fsp = clzName.lastIndexOf('/');
            File file = new File(ERROR_PATH, clzName.substring(0, fsp));
            try {
                file.mkdirs();
                File clzFile = new File(file, clzName.substring(fsp + 1) + ".class");
                Files.write(clzFile.toPath(), generator.generateClzData());
                log.info("dumped class file:{}", clzFile.getAbsolutePath());
            } catch (IOException ex) {
                log.warn("error write class data", ex);
            }
            handle = null;
        }
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return Maybe.create(emitter -> createInterpreterVm(root, attach, emitter).exec());
    }

    @Override
    public Maybe<JsonNode> aotExec(JsonNode root, Object attach) {
        return Maybe.create(emitter -> createAotVm(root, attach, emitter).exec());
    }

    public InterpreterVm createInterpreterVm(JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) {
        return new InterpreterVm(compiled, root, attach, resultEmitter);
    }

    public AbstractVm createAotVm(JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) throws Exception {
        if (handle == null) {
            throw new IllegalStateException("aot compile failed");
        }
        try {
            return (AbstractVm) handle.invoke(root, attach, resultEmitter);
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
