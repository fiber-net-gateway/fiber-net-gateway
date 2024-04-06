package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.test.TestInIOThreadParent;
import lua.test.MyLib;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;

public class AotClassGeneratorTest extends TestInIOThreadParent {

    @Test
    public void g() throws Throwable {
        testScript("/test.js");
        testScript("/async.js");
        testScript("/break_for.js");
    }

    @Test
    public void gExp() throws Throwable {
        testScript("/exp.js");
    }


    private void testScript(String file) throws Throwable {
        String resourceStr = getResourceStr(file);
        CompiledScript compiledScript = CompiledScript.createNonOptimise(resourceStr, new MyLib());
        generateFile(compiledScript);
        generateAndInvoke(compiledScript);
    }

    private static void generateFile(CompiledScript compiledScript) throws Throwable {
        AotClassGenerator generator = new AotClassGenerator(compiledScript.getCompiled());
        byte[] bytes = generator.generateClzData();
        String clzFile = generator.getGeneratedClzName();
        System.out.println(clzFile.replace('/', '.'));

        int i = clzFile.lastIndexOf('/');
        File path = new File("dist/" + clzFile.substring(0, i));
        path.mkdirs();
        Files.write(new File(path, clzFile.substring(i + 1) + ".class").toPath(), bytes);
    }

    private static void generateAndInvoke(CompiledScript compiledScript) throws Throwable {
        AotClassGenerator generator = new AotClassGenerator(compiledScript.getCompiled());
        Class<?> clz = generator.generateClz();
        Constructor<?> constructor = clz.getConstructor(JsonNode.class, Object.class);
        AbstractVm abstractVm = (AbstractVm) constructor.newInstance(NullNode.getInstance(), null);
        Maybe<JsonNode> nodeMaybe = abstractVm.exec();
        System.out.println(nodeMaybe);
    }

}