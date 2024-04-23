package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ComparedMayBeObserver;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.InterpreterVm;
import io.fiber.net.test.TestInIOThreadParent;
import lua.test.MyLib;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;

public class AotClassGeneratorTest extends TestInIOThreadParent {

    @Test
    public void g() throws Throwable {
        testScript("/test.js");
        testScript("/async.js");
        testScript("/break_for.js");
    }

    @Test
    public void b() throws Throwable {
        testScript("/e3.js");
    }

    @Test
    public void gExp() throws Throwable {
        testScript("/exp.js");
    }

    @Test
    public void var() throws Throwable {
        testScript("/var.js");
    }

    @Test
    public void exception() throws Throwable {
        testScript("/exception.js");
    }

    @Test
    public void f() throws Throwable {
        testScript("/for.js");
    }

    @Test
    public void s() throws Throwable {
        testScript("/sleep.js");
    }

    @Test
    public void o() throws Throwable {
        testScript("/obj.js");
    }


    private void testScript(String file) throws Throwable {
        String resourceStr = getResourceStr(file);
        Compiled compiled = CompilerNodeVisitor.compileFromScript(resourceStr, new MyLib());
        generateAndInvoke(compiled, file);
    }

    private static Class<?> generateFile(Compiled compiled) throws Throwable {
        AotClassGenerator generator = new AotClassGenerator(compiled);
        byte[] bytes = generator.generateClzData();
        String clzFile = generator.getGeneratedClzName();
        System.out.println(clzFile.replace('/', '.'));

        int i = clzFile.lastIndexOf('/');
        File path = new File("dist/" + clzFile.substring(0, i));
        path.mkdirs();
        Files.write(new File(path, clzFile.substring(i + 1) + ".class").toPath(), bytes);
        return generator.loadAsClz(bytes);
    }

    private static void generateAndInvoke(Compiled compiled, String name) throws Throwable {
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);


        InterpreterVm.createFromCompiled(compiled, NullNode.getInstance(), null).exec().subscribe(observer.getOb());


        Class<?> clz = generateFile(compiled);
        Constructor<?> constructor = clz.getConstructor(JsonNode.class, Object.class);
        AbstractVm abstractVm = (AbstractVm) constructor.newInstance(NullNode.getInstance(), null);
        Maybe<JsonNode> nodeMaybe = abstractVm.exec();
        nodeMaybe.subscribe(observer.getOb());
    }


}