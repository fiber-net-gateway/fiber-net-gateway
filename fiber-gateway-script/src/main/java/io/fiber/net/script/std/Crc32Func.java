package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class Crc32Func implements Library.Function {
    @Override
    public void call(ExecutionContext context, JsonNode... args) {
        if (ArrayUtils.isEmpty(args)) {
            context.returnVal(this, LongNode.valueOf(0));
            return;
        }
        String s = args[0].asText(null);
        if (StringUtils.isEmpty(s)) {
            context.returnVal(this, LongNode.valueOf(0));
            return;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(s.getBytes(StandardCharsets.UTF_8));
        long value = crc32.getValue();
        context.returnVal(this, LongNode.valueOf(value));
    }
}
