package io.fiber.net.script;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

public class FunctionSignatureTest {

    @Test
    public void shouldMatchStrictVariadicArguments() throws Throwable {
        StdLibrary library = testLibrary();

        Assert.assertEquals(2, execInt("return foo(1,2);", library));
        Assert.assertEquals(3, execInt("return foo(1,2,3);", library));
        Assert.assertEquals(4, execInt("return foo(1,2,...[3,4]);", library));
        Assert.assertEquals(5, execInt("return foo(1,2,...[3,4],5);", library));

        assertNotMatch("return foo(1);", library);
        assertNotMatch("return foo(1,...[2]);", library);
        assertNotMatch("return foo();", library);
    }

    @Test
    public void shouldCompileMissingDefaultArgumentsAsNormalArgs() throws Throwable {
        StdLibrary library = testLibrary();

        Assert.assertEquals(7, execInt("return def(1);", library));
        Assert.assertEquals(2, execInt("return def(1,2);", library));
        Assert.assertEquals(3, execInt("return argcDef(1);", library));
    }

    @Test
    public void shouldRejectUnsupportedDefaultLiteral() {
        try {
            FunctionParam.optional("bad", JsonUtil.createObjectNode());
            Assert.fail("expected illegal default value");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static int execInt(String script, Library library) throws Throwable {
        Script interpreter = Script.compileWithoutOptimization(script, library, true);
        Script aot = Script.aotCompileWithoutOptimization(script, library, true);
        JsonNode root = NullNode.getInstance();
        int interpreterVal = interpreter.execForSync(root, null).asInt();
        int aotVal = aot.execForSync(root, null).asInt();
        Assert.assertEquals(interpreterVal, aotVal);
        return interpreterVal;
    }

    private static void assertNotMatch(String script, Library library) {
        try {
            Script.compileWithoutOptimization(script, library, true);
            Assert.fail("expected function argument mismatch");
        } catch (ParseException expected) {
            // expected
        }
    }

    private static StdLibrary testLibrary() {
        StdLibrary library = new StdLibrary();
        library.putFunc("foo",
                new FunctionSignature("foo", true,
                        FunctionParam.required("a"),
                        FunctionParam.required("b"),
                        FunctionParam.variadic("args")),
                context -> IntNode.valueOf(context.getArgCnt()));
        library.putFunc("def",
                new FunctionSignature("def", true,
                        FunctionParam.required("a"),
                        FunctionParam.optional("b", IntNode.valueOf(7)),
                        FunctionParam.optional("c", TextNode.valueOf("x"))),
                context -> IntNode.valueOf(context.getArgVal(1).asInt()));
        library.putFunc("argcDef",
                new FunctionSignature("argcDef", true,
                        FunctionParam.required("a"),
                        FunctionParam.optional("b", IntNode.valueOf(7)),
                        FunctionParam.optional("c", TextNode.valueOf("x"))),
                context -> IntNode.valueOf(context.getArgCnt()));
        return library;
    }
}
