package io.fiber.net.script.lib;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.*;
import io.fiber.net.script.*;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

public class ReflectLibTest {

    @Test
    public void shouldRegisterSyncFunctionsAndConstants() throws Throwable {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports("u-"));
        ReflectLib.registerStatic(library, StaticExports.class);

        JsonNode value = exec("return join('a', 'b') + '-' + argc(1, 2) + '-' + sum(1, 2, 3) + '-' + $test.answer;", library);

        Assert.assertEquals("u-ab-4-6-42", value.asText());
    }

    @Test
    public void shouldUseDeclaredSignatureForArguments() throws Throwable {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports(""));

        Assert.assertEquals(4, exec("return argc(1);", library).asInt());

        try {
            Script.compileWithoutOptimization("return argc();", library, true);
            Assert.fail("expected argument mismatch");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    @Test
    public void shouldRegisterAsyncFunctionAndConstant() {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports(""));

        Script script = Script.compileWithoutOptimization("return asyncAdd(1, 2) + $test.asyncAnswer;", library, true);
        TestObserver observer = new TestObserver();
        script.exec(NullNode.getInstance(), null).subscribe(observer);

        Assert.assertNull(observer.error);
        Assert.assertEquals(45, observer.value.asInt());
    }

    @Test
    public void shouldWrapDirectAotRuntimeExceptions() throws Throwable {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports(""));

        try {
            Script.aotCompileWithoutOptimization("return boom();", library, true)
                    .execForSync(NullNode.getInstance(), null);
            Assert.fail("expected sync script error");
        } catch (ScriptExecException expected) {
            Assert.assertTrue(expected.getMessage().contains("sync boom"));
        }

        TestObserver observer = new TestObserver();
        Script.aotCompileWithoutOptimization("return asyncBoom();", library, true)
                .exec(NullNode.getInstance(), null)
                .subscribe(observer);

        Assert.assertNull(observer.value);
        Assert.assertTrue(observer.error instanceof ScriptExecException);
        Assert.assertTrue(observer.error.getMessage().contains("async boom"));
    }

    @Test
    public void shouldRejectInvalidThrows() throws Exception {
        try {
            new ReflectFunction(BadExports.class.getMethod("badSync"));
            Assert.fail("expected invalid sync throws");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new ReflectAsyncFunction(BadExports.class.getMethod("badAsync", Library.AsyncHandle.class));
            Assert.fail("expected invalid async throws");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void shouldRejectMisorderedArguments() throws Exception {
        try {
            new ReflectFunction(BadExports.class.getMethod("badOrder", JsonNode.class, ExecutionContext.class));
            Assert.fail("expected invalid order");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static JsonNode exec(String script, Library library) throws Throwable {
        Script interpreter = Script.compileWithoutOptimization(script, library, true);
        Script aot = Script.aotCompileWithoutOptimization(script, library, true);
        JsonNode root = NullNode.getInstance();
        JsonNode interpreterValue = interpreter.execForSync(root, null);
        JsonNode aotValue = aot.execForSync(root, null);
        Assert.assertEquals(interpreterValue, aotValue);
        return interpreterValue;
    }

    @ScriptLib(namespace = "$test")
    public static class Exports {
        private final String prefix;

        public Exports(String prefix) {
            this.prefix = prefix;
        }

        @ScriptFunction(name = "join")
        public JsonNode join(@ScriptParam("a") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
            return TextNode.valueOf(prefix + a.asText() + b.asText());
        }

        @ScriptFunction(name = "argc", params = {
                @ScriptParam("a"),
                @ScriptParam(value = "b", optional = true, defaultValue = "2")
        })
        public JsonNode argc(Library.Arguments args) {
            return IntNode.valueOf(args.getArgCnt() + args.getArgVal(1).asInt());
        }

        @ScriptFunction(name = "asyncAdd")
        public void asyncAdd(Library.AsyncHandle handle,
                             @ScriptParam("a") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
            handle.returnVal(IntNode.valueOf(a.asInt() + b.asInt()));
        }

        @ScriptFunction(name = "boom")
        public JsonNode boom() {
            throw new IllegalStateException("sync boom");
        }

        @ScriptFunction(name = "asyncBoom")
        public void asyncBoom(Library.AsyncHandle handle) {
            throw new IllegalStateException("async boom");
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

    public static class StaticExports {
        @ScriptFunction(name = "sum")
        public static JsonNode sum(@ScriptParam("values") JsonNode... values) {
            int sum = 0;
            for (JsonNode value : values) {
                sum += value.asInt();
            }
            return IntNode.valueOf(sum);
        }
    }

    public static class BadExports {
        @ScriptFunction(name = "badSync")
        public static JsonNode badSync() throws Exception {
            return NullNode.getInstance();
        }

        @ScriptFunction(name = "badAsync")
        public static void badAsync(Library.AsyncHandle handle) throws ScriptExecException {
        }

        @ScriptFunction(name = "badOrder")
        public static JsonNode badOrder(@ScriptParam("a") JsonNode node, ExecutionContext context) {
            return node;
        }
    }

    private static class TestObserver implements Maybe.Observer<JsonNode> {
        JsonNode value;
        Throwable error;
        boolean complete;

        @Override
        public void onSubscribe(Disposable d) {
        }

        @Override
        public void onSuccess(JsonNode jsonNode) {
            value = jsonNode;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
