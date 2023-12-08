package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.Library;

import java.util.HashMap;
import java.util.Map;

public class MathFuncs {

    private static NumericNode assertNumNode(JsonNode[] args, int minLen) throws ScriptExecException {
        assert minLen >= 1;
        if (args == null || minLen > args.length || !args[0].isNumber()) {
            throw new ScriptExecException("require numeric value. and len " + minLen);
        }
        return (NumericNode) args[0];
    }

    static class Floor implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            NumericNode numNode;
            try {
                numNode = assertNumNode(args, 1);
            } catch (ScriptExecException e) {
                context.throwErr(this, e);
                return;
            }
            context.returnVal(this, IntNode.valueOf((int) Math.floor(numNode.doubleValue())));
        }
    }

    static class Abs implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            NumericNode numNode;
            try {
                numNode = assertNumNode(args, 1);
            } catch (ScriptExecException e) {
                context.throwErr(this, e);
                return;
            }
            if (numNode.isInt()) {
                context.returnVal(this, IntNode.valueOf(Math.abs(numNode.intValue())));
            } else if (numNode.isLong()) {
                context.returnVal(this, LongNode.valueOf(Math.abs(numNode.longValue())));
            } else if (numNode.isShort()) {
                context.returnVal(this, ShortNode.valueOf((short) Math.abs(numNode.shortValue())));
            } else {
                context.returnVal(this, DoubleNode.valueOf(Math.abs(numNode.doubleValue())));
            }
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("math.floor", new Floor());
        FUNC.put("math.abs", new Abs());
    }

}
