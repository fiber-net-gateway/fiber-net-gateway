package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;
import io.fiber.net.script.run.InterpreterVm;
import org.junit.Assert;
import org.junit.Test;

public class BytecodeTest {

    @Test
    public void shouldDecodeOperandInstructions() {
        Bytecode loadConst = Bytecode.decode(2, (17 << 8) | Code.LOAD_CONST);
        Assert.assertEquals(2, loadConst.pc());
        Assert.assertEquals(Op.LOAD_CONST, loadConst.op());
        Assert.assertEquals(17, loadConst.operandIndex());
        Assert.assertTrue(loadConst.hasOperandIndex());
        Assert.assertEquals(1, loadConst.stackDelta());

        Bytecode propSet = Bytecode.decode((3 << 8) | Code.PROP_SET);
        Assert.assertEquals(Op.PROP_SET, propSet.op());
        Assert.assertEquals(3, propSet.operandIndex());
        Assert.assertEquals(-1, propSet.stackDelta());
        Assert.assertTrue(propSet.mayThrow());
    }

    @Test
    public void shouldDecodeVarInstructions() {
        Bytecode load = Bytecode.decode((5 << 8) | Code.LOAD_VAR);
        Assert.assertEquals(Op.LOAD_VAR, load.op());
        Assert.assertEquals(5, load.varIndex());
        Assert.assertTrue(load.isVarLoad());
        Assert.assertEquals(1, load.stackDelta());

        Bytecode store = Bytecode.decode((6 << 8) | Code.STORE_VAR);
        Assert.assertEquals(Op.STORE_VAR, store.op());
        Assert.assertEquals(6, store.varIndex());
        Assert.assertTrue(store.isVarStore());
        Assert.assertEquals(-1, store.stackDelta());
    }

    @Test
    public void shouldDecodeJumpInstructions() {
        Bytecode jump = Bytecode.decode(1, (12 << 8) | Code.JUMP);
        Assert.assertEquals(Op.JUMP, jump.op());
        Assert.assertEquals(12, jump.targetPc());
        Assert.assertTrue(jump.isJump());
        Assert.assertTrue(jump.isUnconditionalJump());
        Assert.assertFalse(jump.isConditionalJump());

        Bytecode conditional = Bytecode.decode(2, (9 << 8) | Code.JUMP_IF_FALSE);
        Assert.assertEquals(Op.JUMP_IF_FALSE, conditional.op());
        Assert.assertEquals(9, conditional.targetPc());
        Assert.assertTrue(conditional.isConditionalJump());
        Assert.assertEquals(-1, conditional.stackDelta());
    }

    @Test
    public void shouldDecodeFunctionCalls() {
        Bytecode call = Bytecode.decode((44 << 16) | (3 << 8) | Code.CALL_FUNC);
        Assert.assertEquals(Op.CALL_FUNC, call.op());
        Assert.assertEquals(44, call.operandIndex());
        Assert.assertEquals(3, call.argCount());
        Assert.assertTrue(call.isFunctionCall());
        Assert.assertFalse(call.isAsync());
        Assert.assertEquals(-2, call.stackDelta());

        Bytecode async = Bytecode.decode((9 << 16) | (2 << 8) | Code.CALL_ASYNC_FUNC);
        Assert.assertEquals(Op.CALL_ASYNC_FUNC, async.op());
        Assert.assertEquals(9, async.operandIndex());
        Assert.assertEquals(2, async.argCount());
        Assert.assertTrue(async.isAsync());
        Assert.assertEquals(-1, async.stackDelta());

        Bytecode spread = Bytecode.decode((7 << 8) | Code.CALL_FUNC_SPREAD);
        Assert.assertEquals(Op.CALL_FUNC_SPREAD, spread.op());
        Assert.assertEquals(7, spread.operandIndex());
        Assert.assertEquals(1, spread.argCount());
        Assert.assertTrue(spread.isSpreadCall());
        Assert.assertEquals(0, spread.stackDelta());
    }

    @Test
    public void shouldDecodeIteratorVarInstructions() {
        int raw = (13 << InterpreterVm.ITERATOR_OFF)
                | (4 << InterpreterVm.INSTRUMENT_LEN)
                | Code.ITERATE_KEY;

        Bytecode bytecode = Bytecode.decode(raw);
        Assert.assertEquals(Op.ITERATE_KEY, bytecode.op());
        Assert.assertEquals(13, bytecode.iteratorIndex());
        Assert.assertEquals(4, bytecode.storeIndex());
        Assert.assertTrue(bytecode.hasIteratorIndex());
        Assert.assertTrue(bytecode.hasStoreIndex());
        Assert.assertTrue(bytecode.isVarStore());
        Assert.assertEquals(0, bytecode.stackDelta());
    }

    @Test
    public void shouldExposeOpClassificationAndStackDelta() {
        Assert.assertTrue(Op.BOP_PLUS.isBinary());
        Assert.assertEquals(-1, Bytecode.decode(Code.BOP_PLUS).stackDelta());
        Assert.assertTrue(Op.UNARY_NEG.isUnary());
        Assert.assertEquals(0, Bytecode.decode(Code.UNARY_NEG).stackDelta());

        Assert.assertTrue(Bytecode.decode(Code.THROW_EXP).isTerminal());
        Assert.assertTrue(Bytecode.decode(Code.THROW_EXP).isThrow());
        Assert.assertTrue(Bytecode.decode(Code.THROW_EXP).mayThrow());
        Assert.assertEquals(-1, Bytecode.decode(Code.THROW_EXP).stackDelta());

        Assert.assertTrue(Bytecode.decode(Code.END_RETURN).isTerminal());
        Assert.assertTrue(Bytecode.decode(Code.END_RETURN).isReturn());
        Assert.assertFalse(Bytecode.decode(Code.END_RETURN).mayThrow());
    }

    @Test
    public void shouldClassifyOnlyScriptThrowingOpcodesAsMayThrow() {
        assertMayThrow(Code.IDX_SET, Code.IDX_SET_1, Code.PROP_SET, Code.PROP_SET_1,
                Code.BOP_PLUS, Code.BOP_MINUS, Code.BOP_MULTIPLY, Code.BOP_DIVIDE, Code.BOP_MOD,
                Code.UNARY_PLUS, Code.UNARY_MINUS,
                Code.CALL_FUNC, Code.CALL_FUNC_SPREAD, Code.CALL_ASYNC_FUNC, Code.CALL_ASYNC_FUNC_SPREAD,
                Code.CALL_CONST, Code.CALL_ASYNC_CONST, Code.THROW_EXP);

        assertNoThrow(Code.EXP_OBJECT, Code.EXP_ARRAY, Code.PUSH_ARRAY,
                Code.IDX_GET, Code.PROP_GET,
                Code.BOP_MATCH, Code.BOP_LT, Code.BOP_LTE, Code.BOP_GT, Code.BOP_GTE,
                Code.BOP_EQ, Code.BOP_SEQ, Code.BOP_NE, Code.BOP_SNE, Code.BOP_IN,
                Code.UNARY_NEG, Code.UNARY_TYPEOF,
                Code.ITERATE_INTO, Code.ITERATE_NEXT, Code.ITERATE_KEY, Code.ITERATE_VALUE);
    }

    @Test
    public void shouldDecodeAllWithPc() {
        int[] codes = {
                Code.LOAD_ROOT,
                (8 << 8) | Code.LOAD_CONST,
                (5 << 8) | Code.JUMP
        };

        Bytecode[] bytecodes = Bytecode.decodeAll(codes);
        Assert.assertEquals(3, bytecodes.length);
        Assert.assertEquals(0, bytecodes[0].pc());
        Assert.assertEquals(Op.LOAD_ROOT, bytecodes[0].op());
        Assert.assertEquals(1, bytecodes[1].pc());
        Assert.assertEquals(8, bytecodes[1].operandIndex());
        Assert.assertEquals(2, bytecodes[2].pc());
        Assert.assertEquals(5, bytecodes[2].targetPc());
    }

    @Test
    public void shouldRejectUnknownOpcode() {
        try {
            Bytecode.decode(0);
            Assert.fail("expected unknown opcode");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("unknown opcode"));
        }
    }

    private static void assertMayThrow(int... codes) {
        for (int code : codes) {
            Assert.assertTrue("opcode should throw: " + code, Bytecode.decode(code).mayThrow());
        }
    }

    private static void assertNoThrow(int... codes) {
        for (int code : codes) {
            Assert.assertFalse("opcode should not throw: " + code, Bytecode.decode(code).mayThrow());
        }
    }
}
