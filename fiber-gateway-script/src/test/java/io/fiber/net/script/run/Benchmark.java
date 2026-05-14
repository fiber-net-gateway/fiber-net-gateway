package io.fiber.net.script.run;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.script.aot.AsyncSpillAnalysis;
import io.fiber.net.script.aot.Cfg;
import io.fiber.net.script.aot.CfgAotClassGenerator;
import io.fiber.net.script.aot.LivenessAnalysis;
import io.fiber.net.script.aot.SsaDestruction;
import io.fiber.net.script.aot.ValueAllocator;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.parse.OptimiserNodeVisitor;
import lua.test.MyLib;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;

public class Benchmark {

    public static void main(String[] args) throws Throwable {
        try (InputStream resource = Benchmark.class.getResourceAsStream("/for.js")) {
            String string = IOUtils.toString(resource);
            Compiled compiled = CompilerNodeVisitor.compileFromScript(string, new MyLib());

            Class<?> clz = buildGenerator(compiled).loadAsClass();

            long l = System.currentTimeMillis();
            run(compiled);
//            aotRun(clz);
            System.out.println(System.currentTimeMillis() - l);
        }
    }

    private static void run(Compiled compiled) {
        for (int i = 0; i < 100000000; i++) {
            InterpreterVm fromCompiled = InterpreterVm.createFromCompiled(compiled,
                    NullNode.getInstance(),
                    null,
                    OptimiserNodeVisitor.noopEmitter());
            fromCompiled.exec();
        }
    }

    private static void aotRun(Class<?> clz) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle handle = lookup.findConstructor(clz,
                MethodType.methodType(void.class, JsonNode.class, Object.class, Maybe.Emitter.class));
        for (int i = 0; i < 1000000000; i++) {
            AbstractVm abstractVm = (AbstractVm) handle.invoke((JsonNode) NullNode.getInstance(),
                    (Object) null,
                    OptimiserNodeVisitor.noopEmitter());
            abstractVm.exec();
        }
    }

    private static CfgAotClassGenerator buildGenerator(Compiled compiled) {
        Cfg cfg = new Cfg.Builder(compiled).build();
        LivenessAnalysis.Result liveness = new LivenessAnalysis(cfg).analyze();
        AsyncSpillAnalysis.Result asyncSpills = new AsyncSpillAnalysis(cfg, liveness).analyze();
        SsaDestruction.Result ssaDestruction = new SsaDestruction(cfg).analyze();
        ValueAllocator.Result allocation = new ValueAllocator(cfg, liveness, asyncSpills, ssaDestruction).allocate();
        return new CfgAotClassGenerator(cfg, compiled, asyncSpills, ssaDestruction, allocation);
    }
}
