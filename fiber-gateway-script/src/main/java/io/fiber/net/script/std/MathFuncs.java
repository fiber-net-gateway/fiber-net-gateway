package io.fiber.net.script.std;

import io.fiber.net.common.json.*;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Map;

public class MathFuncs {

    private static NumericNode assertNumNode(ExecutionContext context, int minLen) throws ScriptExecException {
        assert minLen >= 1;
        if (minLen > context.getArgCnt() || !context.getArgVal(0).isNumber()) {
            throw new ScriptExecException("require numeric value. and len " + minLen);
        }
        return (NumericNode) context.getArgVal(0);
    }

    static class Floor implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            NumericNode numNode = assertNumNode(context, 1);
            return IntNode.valueOf((int) Math.floor(numNode.doubleValue()));
        }
    }

    static class Abs implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            NumericNode numNode = assertNumNode(context, 1);
            if (numNode.isInt()) {
                return IntNode.valueOf(Math.abs(numNode.intValue()));
            } else if (numNode.isLong()) {
                return LongNode.valueOf(Math.abs(numNode.longValue()));
            } else if (numNode.isShort()) {
                return ShortNode.valueOf((short) Math.abs(numNode.shortValue()));
            } else {
                return DoubleNode.valueOf(Math.abs(numNode.doubleValue()));
            }
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("math.floor", new Floor());
        FUNC.put("math.abs", new Abs());
    }

}
