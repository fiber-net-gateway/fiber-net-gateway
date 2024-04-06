package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class Crc32Func implements Library.Function {
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
        crc32.update(s.getBytes(StandardCharsets.UTF_8));
        long value = crc32.getValue();
        return LongNode.valueOf(value);
    }
}
