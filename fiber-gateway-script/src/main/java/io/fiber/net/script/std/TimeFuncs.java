package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.netty.handler.codec.DateFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TimeFuncs {
    static class NowFunc implements Library.Function {
        @Override
        public boolean isConstExpr() {
            return false;
        }

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return LongNode.valueOf(System.currentTimeMillis());
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

    static class FormatFunc implements Library.Function {
        @Override
        public boolean isConstExpr() {
            return false;
        }

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.noArgs()) {
                return TextNode.valueOf(DateFormatter.format(new Date()));
            }
            SimpleDateFormat format;
            try {
                format = new SimpleDateFormat(context.getArgVal(0).asText());
            } catch (Exception e) {
                throw new ScriptExecException("now function valid format: " + context.getArgVal(0).asText());
            }

            Date date = new Date();
            if (context.getArgCnt() > 1) {
                long time = date.getTime();
                date.setTime(context.getArgVal(1).asLong(time));
            }
            return TextNode.valueOf(format.format(date));
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("time.now", new NowFunc());
        FUNC.put("time.format", new FormatFunc());
    }
}
