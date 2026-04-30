package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.script.run.Compares;

public final class IncludesFunc {
    private IncludesFunc() {
    }

    @ScriptFunction(name = "includes")
    public static JsonNode includes(@ScriptParam("container") JsonNode c,
                                    @ScriptParam(value = "items", variadic = true) JsonNode... items) {
        boolean isText;
        if (!(isText = (c instanceof TextNode)) && !(c instanceof ArrayNode)) {
            return BooleanNode.FALSE;
        }
        if (isText) {
            String t = c.textValue();
            for (JsonNode item : items) {
                if (!item.isTextual() || !t.contains(item.textValue())) {
                    return BooleanNode.FALSE;
                }
            }
        } else {
            for (JsonNode item : items) {
                if (!Compares.includes(c, item)) {
                    return BooleanNode.FALSE;
                }
            }
        }
        return BooleanNode.TRUE;
    }
}
