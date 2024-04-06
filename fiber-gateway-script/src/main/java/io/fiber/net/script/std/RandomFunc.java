package io.fiber.net.script.std;


import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.concurrent.ThreadLocalRandom;

public class RandomFunc implements Library.Function {
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
