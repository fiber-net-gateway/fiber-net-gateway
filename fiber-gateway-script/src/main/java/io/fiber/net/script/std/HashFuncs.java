package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.HashUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

public class HashFuncs {
    static class Crc32Func implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            if (context.noArgs()) {
                return LongNode.valueOf(0);
            }
            String s = context.getArgVal(0).asText(null);
            if (StringUtils.isEmpty(s)) {
                return LongNode.valueOf(0);
            }
            CRC32 crc32 = new CRC32();
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            crc32.update(bytes, 0, bytes.length);
            long value = crc32.getValue();
            return LongNode.valueOf(value);
        }
    }

    static class Md5Func implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return MissingNode.getInstance();
            }

            JsonNode arg = context.getArgVal(0);
            if (arg.isTextual()) {
                return TextNode.valueOf(HashUtils.stringToMD5Str(arg.textValue()));
            }

            if (arg.isBinary()) {
                try {
                    return TextNode.valueOf(HashUtils.bytesToMD5Str(arg.binaryValue()));
                } catch (Exception e) {
                    throw new ScriptExecException(e.getMessage());
                }
            }
            throw new ScriptExecException(arg.getNodeType() + " not support md5");
        }
    }

    static class Sha1Func implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return MissingNode.getInstance();
            }

            JsonNode arg = context.getArgVal(0);
            if (arg.isTextual()) {
                return TextNode.valueOf(HashUtils.hex(HashUtils.sha1(arg.textValue().getBytes())));
            }

            if (arg.isBinary()) {
                try {
                    return TextNode.valueOf(HashUtils.hex(HashUtils.sha1(arg.binaryValue())));
                } catch (Exception e) {
                    throw new ScriptExecException(e.getMessage());
                }
            }
            throw new ScriptExecException(arg.getNodeType() + " not support sha1");
        }
    }

    static class Sha256Func implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return MissingNode.getInstance();
            }

            JsonNode arg = context.getArgVal(0);
            if (arg.isTextual()) {
                return TextNode.valueOf(HashUtils.hex(HashUtils.sha256(arg.textValue().getBytes())));
            }

            if (arg.isBinary()) {
                try {
                    return TextNode.valueOf(HashUtils.hex(HashUtils.sha256(arg.binaryValue())));
                } catch (Exception e) {
                    throw new ScriptExecException(e.getMessage());
                }
            }
            throw new ScriptExecException(arg.getNodeType() + " not support sha256");
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("hash.crc32", new Crc32Func());
        FUNC.put("hash.md5", new Md5Func());
        FUNC.put("hash.sha1", new Sha1Func());
        FUNC.put("hash.sha256", new Sha256Func());
    }

}
