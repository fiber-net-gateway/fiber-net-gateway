package io.fiber.net.script.std;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.LongNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;
import io.netty.handler.codec.DateFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;

@ScriptLib(functionPrefix = "time")
public final class TimeFuncs {
    private TimeFuncs() {
    }

    @ScriptFunction(name = "now", constExpr = false, params = {
            @ScriptParam(value = "format", optional = true, defaultValue = "null")
    })
    public static JsonNode now(JsonNode pattern) throws ScriptExecException {
        if (JsonUtil.isNull(pattern)) {
            return LongNode.valueOf(System.currentTimeMillis());
        }
        SimpleDateFormat format;
        try {
            format = new SimpleDateFormat(pattern.asText());
        } catch (Exception e) {
            throw new ScriptExecException("now function valid format: " + pattern.asText());
        }
        return TextNode.valueOf(format.format(new Date()));
    }

    @ScriptFunction(name = "format", constExpr = false)
    public static JsonNode format() {
        return TextNode.valueOf(DateFormatter.format(new Date()));
    }

    @ScriptFunction(name = "format", constExpr = false, params = {
            @ScriptParam("format"),
            @ScriptParam(value = "timestamp", optional = true, defaultValue = "null")
    })
    public static JsonNode format(JsonNode pattern, JsonNode timestamp) throws ScriptExecException {
        SimpleDateFormat format;
        try {
            format = new SimpleDateFormat(pattern.asText());
        } catch (Exception e) {
            throw new ScriptExecException("now function valid format: " + pattern.asText());
        }

        Date date = new Date();
        if (!JsonUtil.isNull(timestamp)) {
            date.setTime(timestamp.asLong(date.getTime()));
        }
        return TextNode.valueOf(format.format(date));
    }
}
