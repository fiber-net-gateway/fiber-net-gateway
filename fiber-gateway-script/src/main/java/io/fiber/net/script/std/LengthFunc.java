package io.fiber.net.script.std;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

public class LengthFunc implements Library.Function {
    public static final LengthFunc INSTANCE = new LengthFunc();

    @Override
    public JsonNode call(ExecutionContext context) throws ScriptExecException {
        if (context.noArgs()) {
            return IntNode.valueOf(0);
        }

        JsonNode val = context.getArgVal(0);
        if (val.isTextual()) {
            return IntNode.valueOf(val.textValue().length());
        }

        return IntNode.valueOf(val.size());
    }
}
