package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
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

public class ValueAllocatorTest {

    @Test
    public void shouldAllocateRootAndConstAsReloadableLocations() {
        Cfg cfg = build("return $.x == 1;");
        ValueAllocator.Result result = allocate(cfg);
        PropGet propGet = find(cfg, PropGet.class);
        LoadConst one = findAssign(cfg, LoadConst.class);

        Assert.assertEquals(ValueAllocator.Location.Kind.ROOT_FIELD,
                result.locationOf(propGet.getOwner()).getKind());
        Assert.assertEquals(ValueAllocator.Location.Kind.STATIC_LITERAL,
                result.locationOf(one.getResult()).getKind());
        Assert.assertEquals(1, result.staticOperands().getLiterals().size());
        Assert.assertEquals("_LITERAL_0", result.staticOperands().getLiterals().get(0).getFieldName());
    }

    @Test
    public void shouldAllocateCrossAsyncValueAsFieldButKeepRootAndConstReloadable() {
        Cfg cfg = build("let a = $.x; let b = asyncAdd(1, 2); return a + b + 3;");
        ValueAllocator.Result result = allocate(cfg);
        PropGet propGet = find(cfg, PropGet.class);
        LoadConst constant = findAssign(cfg, LoadConst.class);

        ValueAllocator.Location propLocation = result.locationOf(propGet.getResult());
        Assert.assertEquals(ValueAllocator.Location.Kind.ASYNC_FIELD, propLocation.getKind());
        Assert.assertEquals("_async_val_0", ((ValueAllocator.AsyncFieldLocation) propLocation).getFieldName());
        Assert.assertEquals(ValueAllocator.Location.Kind.ROOT_FIELD,
                result.locationOf(propGet.getOwner()).getKind());
        Assert.assertEquals(ValueAllocator.Location.Kind.STATIC_LITERAL,
                result.locationOf(constant.getResult()).getKind());
    }

    @Test
    public void shouldCollectStaticOperandsInInitOrder() {
        Cfg cfg = build("let a = syncAdd(1, 2); let b = $test.answer; let c = $test.asyncAnswer; return asyncAdd(a + b, c);");
        ValueAllocator.Result result = allocate(cfg);

        Object[] init = result.staticOperands().initOperands();
        Assert.assertEquals(3, init.length);
        Assert.assertEquals(2, result.staticOperands().getLiterals().size());
        Assert.assertEquals(0, result.staticOperands().getConstants().size());
        Assert.assertEquals(0, result.staticOperands().getAsyncConstants().size());
        Assert.assertEquals(0, result.staticOperands().getFunctions().size());
        Assert.assertEquals(0, result.staticOperands().getAsyncFunctions().size());
        Assert.assertEquals(1, result.staticOperands().getDirectOwners().size());
        Assert.assertSame(result.staticOperands().getLiterals().get(0).getValue(), init[0]);
        Assert.assertSame(result.staticOperands().getDirectOwners().get(0).getValue(), init[2]);
    }

    @Test
    public void shouldReuseLocalSlotsForNonInterferingValues() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewObj obj = new NewObj(block, 0);
        NewArr arr = new NewArr(block, 1);
        block.getInstructions().add(obj);
        block.getInstructions().add(arr);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertEquals(slot(result, obj.getResult()), slot(result, arr.getResult()));
        Assert.assertEquals(2, result.maxLocal());
        Assert.assertEquals(result.maxLocal(), result.firstTempLocal());
    }

    @Test
    public void shouldSeparateInterferingLocalSlots() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewObj obj = new NewObj(block, 0);
        NewArr arr = new NewArr(block, 1);
        ExpandObj expand = new ExpandObj(block, 2, obj.getResult(), arr.getResult());
        ExpandArr expandArr = new ExpandArr(block, 3, arr.getResult(), obj.getResult());
        block.getInstructions().add(obj);
        block.getInstructions().add(arr);
        block.getInstructions().add(expand);
        block.getInstructions().add(expandArr);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertNotEquals(slot(result, obj.getResult()), slot(result, arr.getResult()));
        Assert.assertEquals(3, result.maxLocal());
    }

    @Test
    public void shouldUseStackLocationForShortLivedBranchCompare() {
        Cfg cfg = build("if ($.x < 2) { return 1; } return 2;");
        ValueAllocator.Result result = allocate(cfg);
        Binary binary = findAssign(cfg, Binary.class);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(binary.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedIterateNext() {
        Cfg cfg = build("for (let _, item of $.items) { return item; } return 0;");
        ValueAllocator.Result result = allocate(cfg);
        Unary iterateNext = findUnary(cfg, Unary.Op.ITERATE_NEXT);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(iterateNext.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedReturnValue() {
        Cfg cfg = build("return $.x;");
        ValueAllocator.Result result = allocate(cfg);
        PropGet propGet = find(cfg, PropGet.class);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(propGet.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedCallArgument() {
        Cfg cfg = build("return syncAdd($.x, 2);");
        ValueAllocator.Result result = allocate(cfg);
        PropGet propGet = find(cfg, PropGet.class);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(propGet.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedPushArrayResult() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewArr array = new NewArr(block, 0);
        LoadConst value = new LoadConst(block, 1, IntNode.valueOf(1));
        PushArr push = new PushArr(block, 2, array.getResult(), value.getResult());
        Ret ret = new Ret(block, 3, push.getResult());
        block.getInstructions().add(array);
        block.getInstructions().add(value);
        block.getInstructions().add(push);
        block.getInstructions().add(ret);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(push.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedExpandArrayResult() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewArr target = new NewArr(block, 0);
        NewArr addition = new NewArr(block, 1);
        ExpandArr expand = new ExpandArr(block, 2, target.getResult(), addition.getResult());
        Ret ret = new Ret(block, 3, expand.getResult());
        block.getInstructions().add(target);
        block.getInstructions().add(addition);
        block.getInstructions().add(expand);
        block.getInstructions().add(ret);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(expand.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedPropSetOwnerResult() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewObj object = new NewObj(block, 0);
        LoadConst value = new LoadConst(block, 1, IntNode.valueOf(1));
        PropSet1 set = new PropSet1(block, 2, object.getResult(), "a", value.getResult());
        Ret ret = new Ret(block, 3, set.getResult());
        block.getInstructions().add(object);
        block.getInstructions().add(value);
        block.getInstructions().add(set);
        block.getInstructions().add(ret);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(set.getResult()).getKind());
    }

    @Test
    public void shouldUseStackLocationForShortLivedIndexSetOwnerResult() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        Block block = cfg.mustGetBlock(0);
        cfg.setEntryBlock(block);
        NewObj object = new NewObj(block, 0);
        LoadConst key = new LoadConst(block, 1, TextNode.valueOf("a"));
        LoadConst value = new LoadConst(block, 2, IntNode.valueOf(1));
        IndexSet1 set = new IndexSet1(block, 3, object.getResult(), key.getResult(), value.getResult());
        Ret ret = new Ret(block, 4, set.getResult());
        block.getInstructions().add(object);
        block.getInstructions().add(key);
        block.getInstructions().add(value);
        block.getInstructions().add(set);
        block.getInstructions().add(ret);

        ValueAllocator.Result result = allocate(cfg);

        Assert.assertEquals(ValueAllocator.Location.Kind.STACK,
                result.locationOf(set.getResult()).getKind());
    }

    @Test
    public void shouldKeepThrowingShortLivedValuesAsLocals() {
        Cfg cfg = build("return $.x + 1;");
        ValueAllocator.Result result = allocate(cfg);
        Binary binary = findAssign(cfg, Binary.class);

        Assert.assertEquals(ValueAllocator.Location.Kind.LOCAL,
                result.locationOf(binary.getResult()).getKind());
    }

    private static ValueAllocator.Result allocate(Cfg cfg) {
        LivenessAnalysis.Result liveness = new LivenessAnalysis(cfg).analyze();
        AsyncSpillAnalysis.Result spills = new AsyncSpillAnalysis(cfg, liveness).analyze();
        SsaDestruction.Result destruction = new SsaDestruction(cfg).analyze();
        return new ValueAllocator(cfg, liveness, spills, destruction).allocate();
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

    private static int slot(ValueAllocator.Result result, SsaValue value) {
        return ((ValueAllocator.LocalLocation) result.locationOf(value)).getSlot();
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

    private static <T extends Expr> T findAssign(Cfg cfg, Class<T> type) {
        for (Block block : cfg.getBlocks()) {
            for (Phi phi : block.getPhiValues()) {
                if (type.isInstance(phi.getResult().getAssign())) {
                    return type.cast(phi.getResult().getAssign());
                }
                for (Phi.Case aCase : phi.getCases()) {
                    if (type.isInstance(aCase.value.getAssign())) {
                        return type.cast(aCase.value.getAssign());
                    }
                }
            }
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Expr && type.isInstance(((Expr) instruction).getResult().getAssign())) {
                    return type.cast(((Expr) instruction).getResult().getAssign());
                }
                for (SsaValue operand : LivenessAnalysis.operandsOf(instruction)) {
                    if (type.isInstance(operand.getAssign())) {
                        return type.cast(operand.getAssign());
                    }
                }
            }
        }
        throw new AssertionError("missing " + type.getSimpleName());
    }

    private static Unary findUnary(Cfg cfg, Unary.Op op) {
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Unary && ((Unary) instruction).getOp() == op) {
                    return (Unary) instruction;
                }
            }
        }
        throw new AssertionError("missing unary " + op);
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
}
