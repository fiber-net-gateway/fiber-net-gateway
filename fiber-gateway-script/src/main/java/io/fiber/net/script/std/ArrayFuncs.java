package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class ArrayFuncs {

    static class ArrayJoinFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                throw new ScriptExecException("array join require array but get none");
            }

            if (!context.getArgVal(0).isArray()) {
                throw new ScriptExecException("array join require array but get " + context.getArgVal(0).getNodeType());
            }

            ArrayNode arr = (ArrayNode) context.getArgVal(0);
            String delimiter = context.getArgCnt() < 2 ? "" : context.getArgVal(1).asText("");
            StringJoiner js = new StringJoiner(delimiter);
            for (JsonNode arg : arr) {
                js.add(arg.asText(""));
            }

            return TextNode.valueOf(js.toString());
        }
    }

    static class ArrayPopFunc implements Library.Function {
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                throw new ScriptExecException("array pop require array but get none");
            }

            if (!context.getArgVal(0).isArray()) {
                throw new ScriptExecException("array pop require array but get " + context.getArgVal(0).getNodeType());
            }

            ArrayNode arr = (ArrayNode) context.getArgVal(0);
            if (arr.isEmpty()) {
                return NullNode.getInstance();
            }
            return arr.remove(arr.size() - 1);
        }
    }

    static class ArrayPushFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                throw new ScriptExecException("array pop require array but get none");
            }

            JsonNode argVal = context.getArgVal(0);
            if (!argVal.isArray()) {
                throw new ScriptExecException("array pop require array but get " + argVal.getNodeType());
            }

            ArrayNode arr = (ArrayNode) argVal;
            int l = context.getArgCnt();
            for (int i = 1; i < l; i++) {
                arr.add(context.getArgVal(i));
            }

            return arr;
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("array.join", new ArrayJoinFunc());
        FUNC.put("array.pop", new ArrayPopFunc());
        FUNC.put("array.push", new ArrayPushFunc());
    }
}
