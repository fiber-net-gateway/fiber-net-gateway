package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.fiber.net.common.utils.ArrayUtils;
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
    public void call(ExecutionContext context, JsonNode... args) {
        if (ArrayUtils.isEmpty(args)) {
            context.returnVal(this, BooleanNode.FALSE);
            return;
        }

        long ratio = args[0].asLong(0L);
        if (ratio <= 0) {
            context.returnVal(this, BooleanNode.FALSE);
            return;
        } else if (ratio >= 100) {
            context.returnVal(this, BooleanNode.TRUE);
            return;
        }

        if (args.length == 1) {
            context.returnVal(this, BooleanNode.valueOf(ThreadLocalRandom.current().nextInt(101) <= ratio));
            return;
        }

        CRC32 crc32 = new CRC32();
        for (int i = 1; i < args.length; i++) {
            String s = args[i].asText();
            if (StringUtils.isEmpty(s)) {
                continue;
            }
            crc32.update(s.getBytes(StandardCharsets.UTF_8));
        }

        context.returnVal(this, BooleanNode.valueOf(crc32.getValue() % 100L < ratio));
    }
}
