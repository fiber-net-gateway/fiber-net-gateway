package io.fiber.net.script.std;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

public class RandFuncs {

    static class RandomFunc implements Library.Function {
        @Override
        public boolean isConstExpr() {
            return false;
        }

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            long t;
            if (context.noArgs()) {
                t = 1000;
            } else {
                JsonNode arg = context.getArgVal(0);
                if (!arg.isNumber()) {
                    throw new ScriptExecException("random argument must be number");
                }
                t = arg.longValue();
            }
            long l = ThreadLocalRandom.current().nextLong(t);
            return LongNode.valueOf(l);
        }
    }

    static class CanaryFunc implements Library.Function {
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
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                crc32.update(bytes, 0, bytes.length);
            }

            return BooleanNode.valueOf(crc32.getValue() % 100L < ratio);
        }
    }


    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("rand.random", new RandomFunc());
        FUNC.put("rand.canary", new CanaryFunc());
    }

}
