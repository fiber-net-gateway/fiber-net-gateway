package io.fiber.net.script.lib;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.*;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ReflectDirectiveTest {

    @Test
    public void shouldResolveDirectiveFunctionsWithDynamicName() throws Throwable {
        JsonNode value = exec("directive c = calc 'p-';\n"
                + "return c.join('a', 'b') + '-' + c.argc(1) + '-' + c.sum(1, 2, 3);");

        Assert.assertEquals("p-ab-4-6", value.asText());
    }

    @Test
    public void shouldResolveAsyncDirectiveFunction() {
        Script script = Script.compileWithoutOptimization("directive c = calc 'p-';\n"
                + "return c.asyncAdd(2, 3);", new DirectiveLibrary(), true);
        TestObserver observer = new TestObserver();
        script.exec(NullNode.getInstance(), null).subscribe(observer);

        Assert.assertNull(observer.error);
        Assert.assertEquals(5, observer.value.asInt());
    }

    @Test
    public void shouldRejectStaticDirectiveFunction() {
        try {
            ReflectDirective.of(new StaticDirective());
            Assert.fail("expected static directive function rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void shouldRejectMissingDirectiveAnnotation() {
        try {
            ReflectDirective.of(new NotDirective());
            Assert.fail("expected missing directive annotation rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static JsonNode exec(String source) throws Throwable {
        DirectiveLibrary library = new DirectiveLibrary();
        Script interpreter = Script.compileWithoutOptimization(source, library, true);
        Script aot = Script.aotCompileWithoutOptimization(source, library, true);
        JsonNode root = NullNode.getInstance();
        JsonNode interpreterValue = interpreter.execForSync(root, null);
        JsonNode aotValue = aot.execForSync(root, null);
        Assert.assertEquals(interpreterValue, aotValue);
        return interpreterValue;
    }

    private static class DirectiveLibrary extends StdLibrary {
        @Override
        public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
            if (!"calc".equals(type)) {
                return null;
            }
            String prefix = literals.isEmpty() ? "" : literals.get(0).getLiteralValue().asText();
            return ReflectDirective.of(new CalcDirective(prefix));
        }
    }

    @ScriptDirective("calc")
    public static class CalcDirective {
        private final String prefix;

        public CalcDirective(String prefix) {
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

        @ScriptFunction(name = "sum")
        public JsonNode sum(@ScriptParam("values") JsonNode... values) {
            int sum = 0;
            for (JsonNode value : values) {
                sum += value.asInt();
            }
            return IntNode.valueOf(sum);
        }

        @ScriptFunction(name = "asyncAdd")
        public void asyncAdd(Library.AsyncHandle handle,
                             @ScriptParam("a") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
            handle.returnVal(IntNode.valueOf(a.asInt() + b.asInt()));
        }
    }

    @ScriptDirective("bad")
    public static class StaticDirective {
        @ScriptFunction(name = "call")
        public static JsonNode call() {
            return NullNode.getInstance();
        }
    }

    public static class NotDirective {
        @ScriptFunction(name = "call")
        public JsonNode call() {
            return NullNode.getInstance();
        }
    }

    private static class TestObserver implements Maybe.Observer<JsonNode> {
        JsonNode value;
        Throwable error;

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
        }
    }
}
