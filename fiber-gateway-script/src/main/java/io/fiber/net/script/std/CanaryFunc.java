package io.fiber.net.script.std;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

public class CanaryFunc implements Library.Function {
    @Override
    public boolean isConstExpr() {
        return false;
    }

    @Override
    public JsonNode call(ExecutionContext context) {
        if (context.noArgs()) {
            return BooleanNode.FALSE;
        }

        long ratio = context.getArgVal(0).asLong(0L);
        if (ratio <= 0) {
            return BooleanNode.FALSE;
        } else if (ratio >= 100) {
            return BooleanNode.TRUE;
        }

        int argCnt = context.getArgCnt();
        if (argCnt == 1) {
            return BooleanNode.valueOf(ThreadLocalRandom.current().nextInt(101) <= ratio);
        }

        CRC32 crc32 = new CRC32();
        for (int i = 1; i < argCnt; i++) {
            String s = context.getArgVal(i).asText();
            if (StringUtils.isEmpty(s)) {
                continue;
            }
            crc32.update(s.getBytes(StandardCharsets.UTF_8));
        }

        return BooleanNode.valueOf(crc32.getValue() % 100L < ratio);
    }
}
