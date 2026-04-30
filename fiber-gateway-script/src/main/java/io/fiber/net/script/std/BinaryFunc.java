package io.fiber.net.script.std;

import io.fiber.net.common.json.BinaryNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.HashUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@ScriptLib(functionPrefix = "binary")
public final class BinaryFunc {
    private BinaryFunc() {
    }

    @ScriptFunction(name = "base64Encode")
    public static JsonNode base64Encode(@ScriptParam("value") JsonNode arg) {
        if (!arg.isBinary()) {
            return MissingNode.getInstance();
        }
        try {
            return TextNode.valueOf(Base64.getEncoder().encodeToString(arg.binaryValue()));
        } catch (IOException ignore) {
            return MissingNode.getInstance();
        }
    }

    @ScriptFunction(name = "base64Decode")
    public static JsonNode base64Decode(@ScriptParam("value") JsonNode arg) {
        if (!arg.isTextual()) {
            return MissingNode.getInstance();
        }
        return BinaryNode.valueOf(Base64.getDecoder().decode(arg.textValue()));
    }

    @ScriptFunction(name = "hex")
    public static JsonNode hex(@ScriptParam("value") JsonNode arg) throws ScriptExecException {
        if (!arg.isBinary()) {
            throw new ScriptExecException(arg.getNodeType() + " is not support hex");
        }
        try {
            return TextNode.valueOf(HashUtils.hex(arg.binaryValue()));
        } catch (IOException e) {
            throw new ScriptExecException(e.getMessage());
        }
    }

    @ScriptFunction(name = "fromHex")
    public static JsonNode fromHex(@ScriptParam("value") JsonNode arg) throws ScriptExecException {
        if (!arg.isTextual()) {
            throw new ScriptExecException(arg.getNodeType() + " is not support hex");
        }
        return BinaryNode.valueOf(HashUtils.fromHex(arg.textValue()));
    }

    @ScriptFunction(name = "getUtf8Bytes")
    public static JsonNode getUtf8Bytes(@ScriptParam("value") JsonNode arg) {
        return BinaryNode.valueOf(JsonUtil.toString(arg).getBytes(StandardCharsets.UTF_8));
    }
}
