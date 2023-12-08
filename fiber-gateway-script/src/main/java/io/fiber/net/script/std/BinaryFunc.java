package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class BinaryFunc {
    static class EncodeBase64 implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.returnVal(this, null);
                return;
            }
            JsonNode arg = args[0];
            if (!arg.isBinary()) {
                context.returnVal(this, null);
                return;
            }
            TextNode node = null;
            try {
                node = TextNode.valueOf(Base64.getEncoder().encodeToString(arg.binaryValue()));
            } catch (IOException ignore) {
                // not hit
            }
            context.returnVal(this, node);
        }
    }

    static class DecodeBase64 implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.returnVal(this, null);
                return;
            }
            JsonNode arg = args[0];
            if (!arg.isTextual()) {
                context.returnVal(this, null);
                return;
            }
            BinaryNode node = BinaryNode.valueOf(Base64.getDecoder().decode(arg.textValue()));
            context.returnVal(this, node);
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("binary.base64Encode", new EncodeBase64());
        FUNC.put("binary.base64Decode", new DecodeBase64());
    }

}
