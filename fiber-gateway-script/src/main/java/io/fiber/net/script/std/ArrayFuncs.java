package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.script.Library;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class ArrayFuncs {

    static class ArrayJoinFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.throwErr(this, new ScriptExecException("array join require array but get none"));
                return;

            }

            if (!args[0].isArray()) {
                context.throwErr(this, new ScriptExecException("array join require array but get " + args[0].getNodeType()));
                return;
            }

            ArrayNode arr = (ArrayNode) args[0];
            String delimiter = args.length < 2 ? "" : args[1].asText("");
            StringJoiner js = new StringJoiner(delimiter);
            for (JsonNode arg : arr) {
                js.add(arg.asText(""));
            }

            context.returnVal(this, TextNode.valueOf(js.toString()));
        }
    }

    static class ArrayPopFunc implements Library.Function {
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.throwErr(this, new ScriptExecException("array pop require array but get none"));
                return;
            }

            if (!args[0].isArray()) {
                context.throwErr(this, new ScriptExecException("array pop require array but get " + args[0].getNodeType()));
                return;
            }

            ArrayNode arr = (ArrayNode) args[0];
            if (arr.isEmpty()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            context.returnVal(this, arr.remove(arr.size() - 1));
        }
    }

    static class ArrayPushFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.throwErr(this, new ScriptExecException("array pop require array but get none"));
                return;
            }

            if (!args[0].isArray()) {
                context.throwErr(this, new ScriptExecException("array pop require array but get " + args[0].getNodeType()));
                return;
            }

            ArrayNode arr = (ArrayNode) args[0];
            int l = args.length;
            for (int i = 1; i < l; i++) {
                arr.add(args[i]);
            }

            context.returnVal(this, arr);
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("arrays.join", new ArrayJoinFunc());
        FUNC.put("arrays.pop", new ArrayPopFunc());
        FUNC.put("arrays.push", new ArrayPushFunc());
    }
}
