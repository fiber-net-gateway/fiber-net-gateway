package io.fiber.net.script.std;

import io.fiber.net.common.json.BigIntegerNode;
import io.fiber.net.common.json.DoubleNode;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.NumericNode;
import io.fiber.net.common.json.ShortNode;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

@ScriptLib(functionPrefix = "math")
public final class MathFuncs {
    private MathFuncs() {
    }

    @ScriptFunction(name = "floor")
    public static JsonNode floor(@ScriptParam("value") JsonNode value) throws ScriptExecException {
        NumericNode numNode = assertNumNode(value);
        if (numNode.isIntegralNumber()) {
            return numNode;
        }
        if (numNode.isFloat()) {
            return IntNode.valueOf((int) Math.floor(numNode.floatValue()));
        }
        if (numNode.isDouble()) {
            return LongNode.valueOf((long) Math.floor(numNode.doubleValue()));
        }
        if (numNode.isBigDecimal()) {
            return BigIntegerNode.valueOf(numNode.decimalValue().toBigInteger());
        }
        return numNode;
    }

    @ScriptFunction(name = "abs")
    public static JsonNode abs(@ScriptParam("value") JsonNode value) throws ScriptExecException {
        NumericNode numNode = assertNumNode(value);
        if (numNode.isInt()) {
            return IntNode.valueOf(Math.abs(numNode.intValue()));
        }
        if (numNode.isLong()) {
            return LongNode.valueOf(Math.abs(numNode.longValue()));
        }
        if (numNode.isShort()) {
            return ShortNode.valueOf((short) Math.abs(numNode.shortValue()));
        }
        return DoubleNode.valueOf(Math.abs(numNode.doubleValue()));
    }

    private static NumericNode assertNumNode(JsonNode value) throws ScriptExecException {
        if (!value.isNumber()) {
            throw new ScriptExecException("require numeric value");
        }
        return (NumericNode) value;
    }
}
