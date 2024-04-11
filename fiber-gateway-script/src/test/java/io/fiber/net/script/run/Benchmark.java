package io.fiber.net.script.run;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import lua.test.MyLib;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.lang.reflect.Constructor;

public class Benchmark {

    public static void main(String[] args) throws Throwable {
        try (InputStream resource = Benchmark.class.getResourceAsStream("/for.js")) {
            String string = IOUtils.toString(resource);
            Compiled compiled = CompilerNodeVisitor.compileFromScript(string, new MyLib());

            AotClassGenerator generator = new AotClassGenerator(compiled);
            Class<?> clz = generator.generateClz();

            long l = System.currentTimeMillis();
//            run(compiled);
            aotRun(clz);
            System.out.println(System.currentTimeMillis() - l);
        }
    }

    private static void run(Compiled compiled) {
        for (int i = 0; i < 100000000; i++) {
            InterpreterVm fromCompiled = InterpreterVm.createFromCompiled(compiled, NullNode.getInstance(), null);
            fromCompiled.exec();
        }
    }

    private static void aotRun(Class<?> clz) throws Exception {

        for (int i = 0; i < 100000000; i++) {
            Constructor<?> constructor = clz.getConstructor(JsonNode.class, Object.class);
            AbstractVm abstractVm = (AbstractVm) constructor.newInstance(NullNode.getInstance(), null);
            abstractVm.exec();
        }
    }
}
