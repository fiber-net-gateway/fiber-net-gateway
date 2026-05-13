package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.lib.ReflectLib;
import io.fiber.net.script.lib.ScriptConstant;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.CompilerNodeVisitor;
import io.fiber.net.script.std.StdLibrary;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

public class LivenessAnalysisTest {

    @Test
    public void shouldExposeInstructionLiveAfter() {
        Cfg cfg = build("let a = $.x; let b = a + 1; return b;");
        PropGet propGet = find(cfg, PropGet.class);
        Binary binary = find(cfg, Binary.class);

        LivenessAnalysis.Result result = new LivenessAnalysis(cfg).analyze();

        Assert.assertTrue(result.liveAfter(propGet).contains(propGet.getResult()));
        Assert.assertFalse(result.liveAfter(binary).contains(propGet.getResult()));
    }

    @Test
    public void shouldNotSpillAsyncFunctionArgumentsOnlyConsumedByCall() {
        Cfg cfg = build("return asyncAdd($.x, 2);");
        CallAsyncFunc call = find(cfg, CallAsyncFunc.class);

        AsyncSpillAnalysis.Result result = new AsyncSpillAnalysis(cfg).analyze();

        Assert.assertTrue(result.getSpillValues(call).isEmpty());
        Assert.assertTrue(result.getSpillValues().isEmpty());
    }

    @Test
    public void shouldSpillValueUsedAfterAsyncFunctionCall() {
        Cfg cfg = build("let a = $.x; let b = asyncAdd(1, 2); return a + b;");
        PropGet propGet = find(cfg, PropGet.class);
        CallAsyncFunc call = find(cfg, CallAsyncFunc.class);

        AsyncSpillAnalysis.Result result = new AsyncSpillAnalysis(cfg).analyze();
        Set<SsaValue> spills = result.getSpillValues(call);

        Assert.assertTrue(spills.contains(propGet.getResult()));
        Assert.assertFalse(spills.contains(call.getResult()));
        Assert.assertEquals(0, result.getSpillId(propGet.getResult()));
    }

    @Test
    public void shouldSpillValueUsedAfterAsyncConstantCall() {
        Cfg cfg = build("let a = $.x; let b = $test.asyncAnswer; return a + b;");
        PropGet propGet = find(cfg, PropGet.class);
        CallAsyncConst call = find(cfg, CallAsyncConst.class);

        AsyncSpillAnalysis.Result result = new AsyncSpillAnalysis(cfg).analyze();

        Assert.assertTrue(result.getSpillValues(call).contains(propGet.getResult()));
        Assert.assertFalse(result.getSpillValues(call).contains(call.getResult()));
    }

    private static Cfg build(String script) {
        Compiled compiled = CompilerNodeVisitor.compileFromScript(script, library());
        return new Cfg.Builder(compiled).build();
    }

    private static StdLibrary library() {
        StdLibrary library = new StdLibrary();
        ReflectLib.register(library, new Exports());
        return library;
    }

    private static <T extends Instruction> T find(Cfg cfg, Class<T> type) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (type.isInstance(instruction)) {
                    return type.cast(instruction);
                }
            }
        }
        throw new AssertionError("missing " + type.getSimpleName());
    }

    @ScriptLib(namespace = "$test")
    public static class Exports {
        @ScriptFunction(name = "asyncAdd")
        public void asyncAdd(Library.AsyncHandle handle,
                             @ScriptParam("a") JsonNode a,
                             @ScriptParam("b") JsonNode b) {
            handle.returnVal(IntNode.valueOf(a.asInt() + b.asInt()));
        }

        @ScriptConstant(key = "asyncAnswer")
        public void asyncAnswer(Library.AsyncHandle handle) {
            handle.returnVal(IntNode.valueOf(42));
        }
    }
}
