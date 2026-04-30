package io.fiber.net.script.std;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

@ScriptLib(functionPrefix = "rand")
public final class RandFuncs {
    private RandFuncs() {
    }

    @ScriptFunction(name = "random", constExpr = false, params = {
            @ScriptParam(value = "max", optional = true, defaultValue = "1000")
    })
    public static JsonNode random(JsonNode max) throws ScriptExecException {
        if (!max.isNumber()) {
            throw new ScriptExecException("random argument must be number");
        }
        return LongNode.valueOf(ThreadLocalRandom.current().nextLong(max.longValue()));
    }

    @ScriptFunction(name = "canary", constExpr = false)
    public static JsonNode canary(@ScriptParam("ratio") JsonNode ratioNode,
                                  @ScriptParam(value = "keys", variadic = true) JsonNode... keys) {
        long ratio = ratioNode.asLong(0L);
        if (ratio <= 0) {
            return BooleanNode.FALSE;
        }
        if (ratio >= 100) {
            return BooleanNode.TRUE;
        }
        if (keys.length == 0) {
            return BooleanNode.valueOf(ThreadLocalRandom.current().nextInt(100) < ratio);
        }

        CRC32 crc32 = new CRC32();
        for (JsonNode key : keys) {
            String s = key.asText();
            if (StringUtils.isEmpty(s)) {
                continue;
            }
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            crc32.update(bytes, 0, bytes.length);
        }
        return BooleanNode.valueOf(crc32.getValue() % 100L < ratio);
    }
}
