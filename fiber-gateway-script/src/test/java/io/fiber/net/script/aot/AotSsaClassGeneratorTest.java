package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ComparedMayBeObserver;
import io.fiber.net.script.Script;
import io.fiber.net.script.parse.InterpreterScript;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.test.TestInIOThreadParent;
import lua.test.MyLib;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;

public class AotSsaClassGeneratorTest extends TestInIOThreadParent {
    @Test
    public void shouldGenerateClassForOptimizedSyncCfg() throws Throwable {
        String script = "let y;\n"
                + "if (1 < 2) {\n"
                + "    y = 1;\n"
                + "} else {\n"
                + "    y = 2;\n"
                + "}\n"
                + "if (y == 1) {\n"
                + "    y = y + 4;\n"
                + "}\n"
                + "return y;\n";
        InterpreterScript interpreter = compile(script);
        Script generated = AotSsaCompiledScript.of(interpreter.getCompiled());

        JsonNode expected = interpreter.execForSync(NullNode.getInstance(), null);
        JsonNode actual = generated.execForSync(NullNode.getInstance(), null);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void shouldGenerateDirectAbstractVmSubclassForSyncScript() throws Throwable {
        InterpreterScript interpreter = compile("let x = 1; return x + 2;");
        SsaVmClassGenerator generator = new SsaVmClassGenerator(interpreter.getCompiled());
        Class<? extends AbstractVm> clazz = generator.loadAsClass();
        Constructor<? extends AbstractVm> constructor = clazz.getConstructor(
                io.fiber.net.common.json.JsonNode.class,
                Object.class,
                io.fiber.net.common.async.Maybe.Emitter.class
        );

        Assert.assertEquals(AbstractVm.class, clazz.getSuperclass());
        Assert.assertNotNull(constructor);
    }

    @Test
    public void shouldRunAsyncInstructionAndCatchException() throws Throwable {
        String script = "let x = 1;\n"
                + "try {\n"
                + "    x = x + sleep(0);\n"
                + "    x = x + sleep(-1);\n"
                + "    x = 99;\n"
                + "} catch(e) {\n"
                + "    x = x + 3;\n"
                + "}\n"
                + "return x;\n";
        compareImmediate(script, "async-catch");
    }

    @Test
    public void shouldRunResourceExceptionScript() throws Throwable {
        compareImmediate(getResourceStr("/ift.js"), "resource-ift");
    }

    private static InterpreterScript compile(String script) {
        return (InterpreterScript) Script.compileWithoutOptimization(script, new MyLib(), true);
    }

    private static void compareImmediate(String script, String name) throws Throwable {
        InterpreterScript interpreter = compile(script);
        Script generated = AotSsaCompiledScript.of(interpreter.getCompiled());
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);
        generated.exec(NullNode.getInstance(), null).subscribe(observer.getOb());
        interpreter.exec(NullNode.getInstance(), null).subscribe(observer.getOb());
        Assert.assertTrue(name, observer.isEndAndSec());
    }
}
