package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.Library;
import io.fiber.net.script.ExecutionContext;

import java.text.SimpleDateFormat;
import java.util.Date;

public class NowFunc implements Library.Function {
    @Override
    public boolean isConstExpr() {
        return false;
    }

    @Override
    public void call(ExecutionContext context, JsonNode... args) {
        if (ArrayUtils.isEmpty(args)) {
            context.returnVal(this, LongNode.valueOf(System.currentTimeMillis() / 1000));
            return;
        }
        SimpleDateFormat format;
        try {
            format = new SimpleDateFormat(args[0].asText());
        } catch (Exception e) {
            context.throwErr(this, new ScriptExecException("now function valid format: " + args[0].asText()));
            return;
        }
        context.returnVal(this, TextNode.valueOf(format.format(new Date())));
    }
}
