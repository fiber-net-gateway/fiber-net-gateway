package io.fiber.net.script.std;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptParam;

import java.io.IOException;

public final class LengthFunc {
    private LengthFunc() {
    }

    @ScriptFunction(name = "length")
    public static JsonNode length(@ScriptParam(defaultValue = "null") JsonNode val) {
        if (JsonUtil.isNull(val)) {
            return IntNode.valueOf(0);
        }
        if (val.isTextual()) {
            return IntNode.valueOf(val.textValue().length());
        }
        if (val.isBinary()) {
            try {
                return IntNode.valueOf(val.binaryValue().length);
            } catch (IOException ignore) {
            }
        }
        return IntNode.valueOf(val.size());
    }
}
