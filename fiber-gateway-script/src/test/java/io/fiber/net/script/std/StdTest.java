package io.fiber.net.script.std;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ComparedMayBeObserver;
import io.fiber.net.script.Script;
import io.fiber.net.test.TestInIOThreadParent;
import org.junit.Assert;
import org.junit.Test;

public class StdTest extends TestInIOThreadParent {

    @Test
    public void test__length() throws Exception {
        expressionAssertTrue("length(\"abc\") === 3");
        expressionAssertTrue("length({a:1,b:2}) === 2");
        expressionAssertTrue("length([1,2,3]) === 3");
        expressionAssertTrue("length(1) === 0");
    }

    @Test
    public void test__includes() throws Exception {
        expressionAssertTrue("includes(\"abcabc\", \"cab\")");
        expressionAssertTrue("includes([\"aa\",\"bb\", \"cc\"], \"aa\")");
        // other false;
        expressionAssertTrue("includes({a:1}, \"a\") === false");
    }

    @Test
    public void test__arrays_push() throws Exception {
        runAndAssertTrue("let a = [1,2];\n" +
                "let b = array.push(a, 3, 4);\n" +
                "// [1,2,3,4]\n" +
                "return a === b &&\n" +
                "    a[0] === 1 &&\n" +
                "    a[1] === 2 &&\n" +
                "    a[2] === 3 &&\n" +
                "    a[3] === 4;", "array.push");
    }

    @Test
    public void test__arrays_pop() throws Exception {
        runAndAssertTrue("let a = [1,2,3];\n" +
                "let b = array.pop(a);\r\n" +
                "let c = array.pop(a);\r\n" +
                "// a -> [1]\r\n" +
                "return length(a) === 1\n" +
                "    && b === 3\n" +
                "    && c === 2;", "array.pop");
    }

    @Test
    public void test__arrays_join() throws Exception {
        runAndAssertTrue("let a = [1,2,3];\n" +
                "let b = array.join(a, \"-\");\n" +
                "return b === \"1-2-3\";", "array.join");
    }

    @Test
    public void test__Object_assign() throws Exception {
        runAndAssertTrue("let a = {a:1,b:2};\n" +
                "let b = Object.assign(a, {c:3});\n" +
                "return a === b && a.a === 1 && a.b === 2 && a.c === 3;", "Object.assign");
    }

    @Test
    public void test__Object_keys() throws Exception {
        runAndAssertTrue("let a = {a:1,b:2};\n" +
                "let b = Object.keys(a);\n" +
                "return typeof b === 'array' \n" +
                "    && length(b) === 2 \n" +
                "    && b[0] === \"a\"\n" +
                "    && b[1] === \"b\";", "Object.keys");
    }

    @Test
    public void test__Object_values() throws Exception {
        runAndAssertTrue("let a = {a:1,b:2};\n" +
                "let b = Object.values(a);\n" +
                "return typeof b === 'array' \n" +
                "    && length(b) === 2 \n" +
                "    && b[0] === 1\n" +
                "    && b[1] === 2;", "Object.values");
    }

    @Test
    public void test__Object_delete() throws Exception {
        runAndAssertTrue("let a = {a:1,b:2};\n" +
                "let b = Object.delete(a, \"a\", \"c\");\n" +
                "return b === 1\n" +
                "    && length(a) === 1 \n" +
                "    && a.b === 2\n" +
                "    && typeof a.a === \"missing\";", "Object.delete");
    }

    private void expressionAssertTrue(String expression) throws Exception {
        runAndAssertTrue("return (" + expression + ");", expression);
    }

    private static void runAndAssertTrue(String script, String name) throws Exception {
        Script compiled = Script.compile(script, StdLibrary.getDefInstance());
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);
        compiled.aotExec(NullNode.getInstance()).subscribe(observer.getOb());
        compiled.exec(NullNode.getInstance()).subscribe(observer.getOb());
        Assert.assertTrue(observer.isEndAndSec());
        Assert.assertSame(BooleanNode.TRUE, observer.getSameResult());
    }

}
