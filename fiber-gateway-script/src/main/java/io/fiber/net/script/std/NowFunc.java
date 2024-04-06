package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.text.SimpleDateFormat;
import java.util.Date;

public class NowFunc implements Library.Function {
    @Override
    public boolean isConstExpr() {
        return false;
    }

    @Override
    public JsonNode call(ExecutionContext context) throws ScriptExecException {
        if (context.noArgs()) {
            return LongNode.valueOf(System.currentTimeMillis() / 1000);
        }
        SimpleDateFormat format;
        try {
            format = new SimpleDateFormat(context.getArgVal(0).asText());
        } catch (Exception e) {
            throw new ScriptExecException("now function valid format: " + context.getArgVal(0).asText());
        }
        return TextNode.valueOf(format.format(new Date()));
    }
}
