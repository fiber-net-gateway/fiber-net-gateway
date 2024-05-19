package io.fiber.net.script.parse;

import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ComparedMayBeObserver;
import io.fiber.net.script.Script;
import io.fiber.net.script.parse.ir.AotClassGenerator;
import io.fiber.net.test.TestInIOThreadParent;
import lua.test.MyLib;
import org.junit.Test;

import java.io.File;
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
        Script script = Script.compileWithoutOptimization(resourceStr, new MyLib(), true);
        generateAndInvoke(script, file);
    }

    private static void generateFile(Compiled compiled) throws Throwable {
        AotClassGenerator generator = new AotClassGenerator(compiled);
        byte[] bytes = generator.generateClzData();
        String clzFile = generator.getGeneratedClzName();
        System.out.println(clzFile.replace('/', '.'));

        int i = clzFile.lastIndexOf('/');
        File path = new File("dist/" + clzFile.substring(0, i));
        path.mkdirs();
        Files.write(new File(path, clzFile.substring(i + 1) + ".class").toPath(), bytes);
    }

    private static void generateAndInvoke(Script script, String name) throws Throwable {
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);
        generateFile(((CompiledScript) script).getCompiled());
        script.aotExec(NullNode.getInstance(), null).subscribe(observer.getOb());
        script.exec(NullNode.getInstance(), null).subscribe(observer.getOb());
    }

}