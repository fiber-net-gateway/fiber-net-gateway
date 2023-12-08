package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Map;

public class JsonFunc {
    static class Parse implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            JsonNode arg = args[0];
            if (!arg.isTextual()) {
                context.throwErr(this, new ScriptExecException("parseJson not support " + arg.getNodeType()));
                return;
            }

            try {
                context.returnVal(this, JsonUtil.MAPPER.readTree(arg.textValue()));
            } catch (Exception e) {
                context.throwErr(this, new ScriptExecException("cannot parseJson: " + e.getMessage(), e));
            }
        }
    }

    static class Stringify implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            try {
                String s = JsonUtil.MAPPER.writeValueAsString(args[0]);
                context.returnVal(this, JsonUtil.createTextNode(s));
            } catch (Exception e) {
                context.throwErr(this, new ScriptExecException("error invoke jsonStringify:" + e.getMessage(), e));
            }

        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("json.parse", new Parse());
        FUNC.put("json.stringify", new Stringify());
    }
}
