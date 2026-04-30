package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.HashUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

@ScriptLib(functionPrefix = "hash")
public final class HashFuncs {
    private HashFuncs() {
    }

    @ScriptFunction(name = "crc32")
    public static JsonNode crc32(@ScriptParam("value") JsonNode value) {
        String s = value.asText(null);
        if (StringUtils.isEmpty(s)) {
            return LongNode.valueOf(0);
        }
        CRC32 crc32 = new CRC32();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return LongNode.valueOf(crc32.getValue());
    }

    @ScriptFunction(name = "md5")
    public static JsonNode md5(@ScriptParam("value") JsonNode arg) throws ScriptExecException {
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

    @ScriptFunction(name = "sha1")
    public static JsonNode sha1(@ScriptParam("value") JsonNode arg) throws ScriptExecException {
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

    @ScriptFunction(name = "sha256")
    public static JsonNode sha256(@ScriptParam("value") JsonNode arg) throws ScriptExecException {
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
