package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.Script;
import io.fiber.net.script.aot.AsyncSpillAnalysis;
import io.fiber.net.script.aot.Cfg;
import io.fiber.net.script.aot.CfgAotClassGenerator;
import io.fiber.net.script.aot.LivenessAnalysis;
import io.fiber.net.script.aot.SsaDestruction;
import io.fiber.net.script.aot.ValueAllocator;
import io.fiber.net.script.ast.Block;
import io.fiber.net.script.run.AbstractVm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;

public class AotCompiledScript implements Script {
    private static final Logger log = LoggerFactory.getLogger(AotCompiledScript.class);
    private static final MethodType METHOD_TYPE = MethodType.methodType(void.class, JsonNode.class, Object.class, Maybe.Emitter.class);
    private static final String ERROR_PATH = SystemPropertyUtil.get("fiber.aotErrorClzDumpPath", "/tmp/fiber_err_clz");
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
        MethodHandle handle;
        CfgAotClassGenerator generator = null;
        try {
            Cfg cfg = new Cfg.Builder(compiled, false).build();
            LivenessAnalysis.Result liveness = new LivenessAnalysis(cfg).analyze();
            AsyncSpillAnalysis.Result asyncSpills = new AsyncSpillAnalysis(cfg, liveness).analyze();
            SsaDestruction.Result ssaDestruction = new SsaDestruction(cfg).analyze();
            ValueAllocator.Result allocation = new ValueAllocator(cfg, liveness, asyncSpills, ssaDestruction).allocate();
            generator = new CfgAotClassGenerator(cfg, compiled, asyncSpills, ssaDestruction, allocation);
            Class<?> aotClz = generator.loadAsClass();
            handle = MethodHandles.lookup().findConstructor(aotClz, METHOD_TYPE);
        } catch (Throwable e) {
            String clzName = generator == null ? "unknown" : generator.getInternalClassName();
            log.error("error create aot VM: clz->{}", clzName, e);
            int fsp = clzName.lastIndexOf('/');
            File file = new File(ERROR_PATH, fsp < 0 ? "" : clzName.substring(0, fsp));
            try {
                file.mkdirs();
                File clzFile = new File(file, clzName.substring(fsp + 1) + ".class");
                if (generator != null) {
                    Files.write(clzFile.toPath(), generator.generateClassData());
                }
                log.info("dumped class file:{}", clzFile.getAbsolutePath());
            } catch (IOException ex) {
                log.warn("error write class data", ex);
            }
            throw new ParseException("could not generate clz", e);
        }
        return new AotCompiledScript(handle, compiled.containsAsyncIS());
    }

    private final MethodHandle handle;
    private final boolean containsAsync;

    public AotCompiledScript(MethodHandle handle, boolean containAsync) {
        this.handle = handle;
        this.containsAsync = containAsync;
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return Maybe.create(emitter -> createAotVm(root, attach, emitter).exec());
    }

    @Override
    public boolean containsAsyncIR() {
        return containsAsync;
    }

    @Override
    public JsonNode execForSync(JsonNode root, Object attach) throws Throwable {
        if (containsAsyncIR()) {
            throw new IllegalStateException("cannot sync exec for async script");
        }
        AbstractVm vm = createAotVm(root, attach, OptimiserNodeVisitor.noopEmitter());
        vm.exec();
        return vm.getResultNow();
    }

    private AbstractVm createAotVm(JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) throws Exception {
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
}
