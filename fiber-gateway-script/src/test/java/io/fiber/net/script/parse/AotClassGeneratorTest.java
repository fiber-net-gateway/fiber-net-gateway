package io.fiber.net.script.parse;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.aot.AsyncSpillAnalysis;
import io.fiber.net.script.aot.Cfg;
import io.fiber.net.script.aot.CfgAotClassGenerator;
import io.fiber.net.script.aot.LivenessAnalysis;
import io.fiber.net.script.aot.SsaDestruction;
import io.fiber.net.script.aot.ValueAllocator;
import io.fiber.net.script.ComparedMayBeObserver;
import io.fiber.net.script.Script;
import io.fiber.net.test.TestInIOThreadParent;
import lua.test.MyLib;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

public class AotClassGeneratorTest extends TestInIOThreadParent {
    @Test
    public void t2() throws Throwable {
        testScript("/p.js");
    }


    @Test
    public void vv() throws Throwable {
        testScript("/test.js");
    }

    @Test
    public void g() throws Throwable {
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

    @Test
    public void bb() throws Throwable {
        testScript("/a.js");
    }

    @Test
    public void b3b() throws Throwable {
        testScript("/cd.js");
    }


    @Test
    public void b1() throws Throwable {
        testScript("/vv.js");
    }

    @Test
    public void off() throws Throwable {
        String resourceStr = getResourceStr("/ift.js");
        Script script = Script.compileWithoutOptimization(resourceStr, new MyLib(), true);
        Maybe<JsonNode> maybe = script.exec(NullNode.getInstance(), null);
        maybe.subscribe((jsonNode, throwable) -> {
            System.out.println(jsonNode);
        });

    }

    @Test
    public void off2() throws Throwable {
        testScript("/ift.js");
    }

    @Test
    public void bb1() throws Throwable {
        testScript("/a.js");
    }

    @Test
    public void b21() throws Throwable {
        testScript("/vv.js");
    }

    @Test
    public void shouldEmitAotSourceFileAndLineNumber() throws Throwable {
        String script = "let a = 1;\n"
                + "let b = 2;\n"
                + "let c = 3;\n"
                + "return panic('x');\n";
        final Throwable[] error = new Throwable[1];
        Script.aotCompileWithoutOptimization("line-info.js", script, new MyLib(), true)
                .exec(NullNode.getInstance(), null)
                .subscribe((jsonNode, throwable) -> error[0] = throwable);
        Assert.assertNotNull(error[0]);

        StackTraceElement runFrame = null;
        for (StackTraceElement element : error[0].getStackTrace()) {
            if (element.getClassName().contains("GeneratedVm_") && "run".equals(element.getMethodName())) {
                runFrame = element;
                break;
            }
        }
        Assert.assertNotNull(stackTrace(error[0]), runFrame);
        Assert.assertEquals("line-info.js", runFrame.getFileName());
        Assert.assertEquals(4, runFrame.getLineNumber());
    }

    private static String stackTrace(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            builder.append(element).append('\n');
        }
        return builder.toString();
    }

    private void testScript(String file) throws Throwable {
        String resourceStr = getResourceStr(file);
        Script script = Script.compileWithoutOptimization(resourceStr, new MyLib(), true);
        generateAndInvoke(script, file);
    }

    private static void generateFile(String name, Compiled compiled) throws Throwable {
        CfgAotClassGenerator generator = buildGenerator(compiled);
        byte[] bytes = generator.generateClassData();
        String clzFile = generator.getInternalClassName();
        System.out.println(name + "->: " + clzFile.replace('/', '.'));

        int i = clzFile.lastIndexOf('/');
        File path = new File("dist/" + clzFile.substring(0, i));
        path.mkdirs();
        Files.write(new File(path, clzFile.substring(i + 1) + ".class").toPath(), bytes);
    }

    private static void generateAndInvoke(Script script, String name) throws Throwable {
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);
        InterpreterScript interpreterScript = (InterpreterScript) script;
        generateFile(name, interpreterScript.getCompiled());
        interpreterScript.createAotCompiledScript().exec(NullNode.getInstance(), null).subscribe(observer.getOb());
        interpreterScript.exec(NullNode.getInstance(), null).subscribe(observer.getOb());
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
