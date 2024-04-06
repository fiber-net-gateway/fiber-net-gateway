package io.fiber.net.script.std;

import io.fiber.net.common.json.BinaryNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class BinaryFunc {
    static class EncodeBase64 implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return MissingNode.getInstance();
            }
            JsonNode arg = context.getArgVal(0);
            if (!arg.isBinary()) {
                return MissingNode.getInstance();
            }
            JsonNode node;
            try {
                node = TextNode.valueOf(Base64.getEncoder().encodeToString(arg.binaryValue()));
            } catch (IOException ignore) {
                // not hit
                node = MissingNode.getInstance();
            }
            return node;
        }
    }

    static class DecodeBase64 implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return MissingNode.getInstance();
            }
            JsonNode arg = context.getArgVal(0);
            if (!arg.isTextual()) {
                return MissingNode.getInstance();
            }
            return BinaryNode.valueOf(Base64.getDecoder().decode(arg.textValue()));
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("binary.base64Encode", new EncodeBase64());
        FUNC.put("binary.base64Decode", new DecodeBase64());
    }

}
