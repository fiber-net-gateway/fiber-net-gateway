package io.fiber.net.script.std;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.io.IOException;

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

        if (val.isBinary()) {
            try {
                return IntNode.valueOf(val.binaryValue().length);
            } catch (IOException ignore) {
            }
        }

        return IntNode.valueOf(val.size());
    }
}
