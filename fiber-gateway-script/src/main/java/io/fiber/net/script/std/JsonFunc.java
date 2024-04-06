package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Map;

public class JsonFunc {
    static class Parse implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            JsonNode arg = context.getArgVal(0);
            if (!arg.isTextual()) {
                throw new ScriptExecException("parseJson not support " + arg.getNodeType());
            }

            try {
                return JsonUtil.MAPPER.readValue(arg.textValue(), JsonNode.class);
            } catch (Exception e) {
                throw new ScriptExecException("cannot parseJson: " + e.getMessage(), e);
            }
        }
    }

    static class Stringify implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            try {
                String s = JsonUtil.MAPPER.writeValueAsString(context.getArgVal(0));
                return JsonUtil.createTextNode(s);
            } catch (Exception e) {
                throw new ScriptExecException("error invoke jsonStringify:" + e.getMessage(), e);
            }

        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("json.parse", new Parse());
        FUNC.put("json.stringify", new Stringify());
    }
}
