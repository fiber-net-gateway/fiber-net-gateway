package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.lib.ReflectLib;
import io.fiber.net.script.lib.ScriptConstant;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CfgAotClassGeneratorTest {

    @Test
    public void shouldGenerateLoadableAbstractVmSubclassSkeleton() throws Exception {
        ValueAllocator.Result allocation = allocate("return 1;");

        CfgAotClassGenerator generator = new CfgAotClassGenerator(allocation,
                "io/fiber/net/script/run/CfgGeneratedVm_TestSkeleton");
        Class<?> clz = generator.loadAsClass();

        Assert.assertTrue(AbstractVm.class.isAssignableFrom(clz));
        Assert.assertNotNull(clz.getDeclaredConstructor(JsonNode.class, Object.class, Maybe.Emitter.class));
        Method run = clz.getDeclaredMethod("run");
        Assert.assertEquals(void.class, run.getReturnType());
        Assert.assertNotNull(clz.getDeclaredMethod(CfgAotClassGenerator.INIT_OPERAND_METHOD, Object[].class));
        assertNoDeclaredField(clz, "currentPc");
        assertNoDeclaredMethod(clz, "getCurrentPc");
    }

    @Test
    public void shouldInitializeStaticOperandFields() throws Exception {
        ValueAllocator.Result allocation = allocate("let a = syncAdd(1, 2); let b = $test.answer; let c = $test.asyncAnswer; return asyncAdd(a + b, c);");

        CfgAotClassGenerator generator = new CfgAotClassGenerator(allocation,
                "io/fiber/net/script/run/CfgGeneratedVm_TestOperands");
        Class<?> clz = generator.loadAsClass();

        assertStaticSame(clz, "_LITERAL_0", allocation.staticOperands().getLiterals().get(0).getValue());
        assertStaticSame(clz, "_CONST_0", allocation.staticOperands().getConstants().get(0).getValue());
        assertStaticSame(clz, "_ASYNC_CONST_0", allocation.staticOperands().getAsyncConstants().get(0).getValue());
        assertStaticSame(clz, "_FUNC_0", allocation.staticOperands().getFunctions().get(0).getValue());
        assertStaticSame(clz, "_ASYNC_FUNC_0", allocation.staticOperands().getAsyncFunctions().get(0).getValue());
    }

    @Test
    public void shouldDeclareAsyncSpillFieldsAndInstantiate() throws Exception {
        ValueAllocator.Result allocation = allocate("let a = $.x; let b = asyncAdd(1, 2); return a + b;");

        CfgAotClassGenerator generator = new CfgAotClassGenerator(allocation,
                "io/fiber/net/script/run/CfgGeneratedVm_TestAsyncFields");
        Class<?> clz = generator.loadAsClass();

        Assert.assertFalse(allocation.asyncFieldValues().isEmpty());
        clz.getDeclaredField("_async_val_0");

        Constructor<?> constructor = clz.getDeclaredConstructor(JsonNode.class, Object.class, Maybe.Emitter.class);
        Object vm = constructor.newInstance(NullNode.getInstance(), null, new NoopEmitter());
        Assert.assertTrue(vm instanceof AbstractVm);
        Assert.assertEquals(-1, ((AbstractVm) vm).getCurrentPc());
    }

    private static void assertNoDeclaredField(Class<?> clz, String fieldName) throws Exception {
        try {
            clz.getDeclaredField(fieldName);
            Assert.fail("unexpected field " + fieldName);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static void assertNoDeclaredMethod(Class<?> clz, String methodName) throws Exception {
        try {
            clz.getDeclaredMethod(methodName);
            Assert.fail("unexpected method " + methodName);
        } catch (NoSuchMethodException ignored) {
        }
    }

    private static void assertStaticSame(Class<?> clz, String fieldName, Object expected) throws Exception {
        Field field = clz.getDeclaredField(fieldName);
        field.setAccessible(true);
        Assert.assertSame(expected, field.get(null));
    }

    private static ValueAllocator.Result allocate(String script) {
        Compiled compiled = CompilerNodeVisitor.compileFromScript(script, library());
        Cfg cfg = new Cfg.Builder(compiled).build();
        LivenessAnalysis.Result liveness = new LivenessAnalysis(cfg).analyze();
        AsyncSpillAnalysis.Result spills = new AsyncSpillAnalysis(cfg, liveness).analyze();
        SsaDestruction.Result destruction = new SsaDestruction(cfg).analyze();
        return new ValueAllocator(cfg, liveness, spills, destruction).allocate();
    }

    private static StdLibrary library() {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports());
        return library;
    }

    @ScriptLib(namespace = "$test")
    public static class Exports {
        @ScriptFunction(name = "syncAdd")
        public JsonNode syncAdd(@ScriptParam("a") JsonNode a,
                                @ScriptParam("b") JsonNode b) {
            return IntNode.valueOf(a.asInt() + b.asInt());
        }

        @ScriptFunction(name = "asyncAdd")
        public void asyncAdd(Library.AsyncHandle handle,
                             @ScriptParam("a") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
            handle.returnVal(IntNode.valueOf(a.asInt() + b.asInt()));
        }

        @ScriptConstant(key = "answer")
        public JsonNode answer() {
            return IntNode.valueOf(42);
        }

        @ScriptConstant(key = "asyncAnswer")
        public void asyncAnswer(Library.AsyncHandle handle) {
            handle.returnVal(IntNode.valueOf(42));
        }
    }

    private static class NoopEmitter implements Maybe.Emitter<JsonNode> {
        @Override
        public void onSuccess(JsonNode jsonNode) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }
}
