package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

@ScriptLib(functionPrefix = "JSON")
public final class JsonFunc {
    private JsonFunc() {
    }

    @ScriptFunction(name = "parse")
    public static JsonNode parse(@ScriptParam("text") JsonNode arg) throws ScriptExecException {
        if (!arg.isTextual()) {
            throw new ScriptExecException("parseJson not support " + arg.getNodeType());
        }
        try {
            return JsonUtil.readTree(arg.textValue());
        } catch (Exception e) {
            throw new ScriptExecException("cannot parseJson: " + e.getMessage(), e);
        }
    }

    @ScriptFunction(name = "stringify")
    public static JsonNode stringify(@ScriptParam("value") JsonNode value) throws ScriptExecException {
        try {
            return JsonUtil.createTextNode(JsonUtil.MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            throw new ScriptExecException("error invoke jsonStringify:" + e.getMessage(), e);
        }
    }
}
