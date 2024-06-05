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


    @Test
    public void test__string_has_prefix() throws Exception {
        runAndAssertTrue("return strings.hasPrefix(\"abcdedf\", \"abc\") === true;", "strings.hasPrefix");
    }

    @Test
    public void test__string_has_suffix() throws Exception {
        runAndAssertTrue("return strings.hasSuffix(\"abcdedf\", \"edf\") === true;", "strings.hasSuffix");
    }

    @Test
    public void test__strings_toLower() throws Exception {
        runAndAssertTrue("return strings.toLower('abc123Abc') === 'abc123abc';", "strings.toLower");
    }

    @Test
    public void test__strings_toUpper() throws Exception {
        runAndAssertTrue("return strings.toUpper('abc123Abc') === 'ABC123ABC';", "strings.toUpper");
    }

    @Test
    public void test__strings_trim() throws Exception {
        runAndAssertTrue("return strings.trim(' \tabc \t ') === 'abc';", "strings.trim");
        runAndAssertTrue("return strings.trim('aaabc a', 'a') === 'bc ';", "strings.trim");
    }

    @Test
    public void test__strings_trimLeft() throws Exception {
        runAndAssertTrue("return strings.trimLeft(' bc a ') === 'bc a '", "strings.trimLeft");
        runAndAssertTrue("return strings.trimLeft('aa bc a', 'a') === ' bc a'", "strings.trimLeft");
    }

    @Test
    public void test__strings_trimRight() throws Exception {
        runAndAssertTrue("return strings.trimRight(' bc a ') === ' bc a'", "strings.trimRight");
        runAndAssertTrue("return strings.trimRight(' bc a aa', 'a') === ' bc a '", "strings.trimRight");
    }

    @Test
    public void test__strings_split() throws Exception {
        runAndAssertTrue("let arr = strings.split('abcecdf', 'c'); return length(arr) === 3" +
                "&& arr[0] === 'ab' && arr[1] === 'e' && arr[2] === 'df'", "strings.split");
    }

    @Test
    public void test__strings_findAll() throws Exception {
        runAndAssertTrue("let arr = strings.findAll(\"abcd-effe-ssf-fd\", \"\\\\w+\");\n" +
                "return length(arr) === 4\n" +
                "  && arr[0] === \"abcd\"\n" +
                "  && arr[1] === \"effe\"\n" +
                "  && arr[2] === \"ssf\"\n" +
                "  && arr[3] === \"fd\";", "strings.findAll");
    }

    @Test
    public void test__strings_contains() throws Exception {
        runAndAssertTrue("return strings.contains(\"abcd-effe-ssf-fd\", \"e-ssf\") === true;", "strings.contains");
    }

    @Test
    public void test__strings_contains_any() throws Exception {
        runAndAssertTrue("return strings.contains_any(\"abcd-effe-ssf-fd\", \"ccddeezzz\") === true;", "strings.contains_any");
    }

    @Test
    public void test__strings_index() throws Exception {
        runAndAssertTrue("return strings.index(\"aabbcc\", \"bcc\") === 3;", "strings.index");
    }

    @Test
    public void test__strings_indexAny() throws Exception {
        runAndAssertTrue("return strings.indexAny('acsdfds', 'rss') === 2", "strings.indexAny");
    }

    @Test
    public void test__strings_lastIndex() throws Exception {
        runAndAssertTrue("return strings.lastIndex('cabcd', 'c') === 3", "strings.lastIndex");
    }

    @Test
    public void test__strings_lastIndexAny() throws Exception {
        runAndAssertTrue("return strings.lastIndexAny('cabcd', 'dcz') === 4", "strings.lastIndexAny");
    }

    @Test
    public void test__strings_repeat() throws Exception {
        runAndAssertTrue("return strings.repeat('acd', 3) === 'acdacdacd'", "strings.repeat");
    }

    @Test
    public void test__strings_match() throws Exception {
        runAndAssertTrue("return strings.match('aaabbbbccc', 'a+b+c+') === true", "strings.match");
    }

    @Test
    public void test__strings_substring() throws Exception {
        runAndAssertTrue("return strings.substring('0123456789', 3) === '3456789' && strings.substring('0123456789', 3, 6) === '345'", "strings.substring");
    }

    @Test
    public void test__strings_toString() throws Exception {
        runAndAssertTrue("return strings.toString(null) === \"null\"\n" +
                "   && strings.toString({}) === \"<ObjectNode>\" && strings.toString(3.5) === '3.5'", "strings.toString");
    }

    private void expressionAssertTrue(String expression) throws Exception {
        runAndAssertTrue("return (" + expression + ");", expression);
    }

    private static void runAndAssertTrue(String script, String name) throws Exception {
        Script compiled = Script.compileWithoutOptimization(script, StdLibrary.getDefInstance(), true);
        ComparedMayBeObserver observer = new ComparedMayBeObserver(name);
        compiled.aotExec(NullNode.getInstance()).subscribe(observer.getOb());
        compiled.exec(NullNode.getInstance()).subscribe(observer.getOb());
        Assert.assertTrue(observer.isEndAndSec());
        Assert.assertSame(BooleanNode.TRUE, observer.getSameResult());
    }

}
