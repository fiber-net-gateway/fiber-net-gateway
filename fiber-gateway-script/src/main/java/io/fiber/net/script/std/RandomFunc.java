package io.fiber.net.script.std;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.Library;

import java.util.concurrent.ThreadLocalRandom;

public class RandomFunc implements Library.Function {
    @Override
    public boolean isConstExpr() {
        return false;
    }

    @Override
    public void call(ExecutionContext context, JsonNode... args) {
        long t;
        if (ArrayUtils.isEmpty(args)) {
            t = 1000;
        } else {
            JsonNode arg = args[0];
            if (!arg.isNumber()) {
                context.throwErr(this, new ScriptExecException("random argument must be number"));
                return;
            }
            t = arg.longValue();
        }
        long l = ThreadLocalRandom.current().nextLong(t);
        context.returnVal(this, LongNode.valueOf(l));
        return;
    }
}
