package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ScriptLib(functionPrefix = "strings")
public final class StringsFuncs {
    private static final TextNode NULL_TEXT = TextNode.valueOf("null");

    private StringsFuncs() {
    }

    @ScriptFunction(name = "hasPrefix")
    public static JsonNode hasPrefix(@ScriptParam("text") JsonNode text,
                                     @ScriptParam("prefix") JsonNode prefix) {
        if (!text.isTextual() || !prefix.isTextual()) {
            return BooleanNode.FALSE;
        }
        return BooleanNode.valueOf(text.textValue().startsWith(prefix.textValue()));
    }

    @ScriptFunction(name = "hasSuffix")
    public static JsonNode hasSuffix(@ScriptParam("text") JsonNode text,
                                     @ScriptParam("suffix") JsonNode suffix) {
        if (!text.isTextual() || !suffix.isTextual()) {
            return BooleanNode.FALSE;
        }
        return BooleanNode.valueOf(text.textValue().endsWith(suffix.textValue()));
    }

    @ScriptFunction(name = "toLower")
    public static JsonNode toLower(@ScriptParam("text") JsonNode text) {
        return text.isTextual() ? TextNode.valueOf(text.textValue().toLowerCase()) : NullNode.getInstance();
    }

    @ScriptFunction(name = "toUpper")
    public static JsonNode toUpper(@ScriptParam("text") JsonNode text) {
        return text.isTextual() ? TextNode.valueOf(text.textValue().toUpperCase()) : NullNode.getInstance();
    }

    @ScriptFunction(name = "trim", params = {
            @ScriptParam("text"),
            @ScriptParam(value = "cutset", optional = true, defaultValue = "null")
    })
    public static JsonNode trim(JsonNode text, JsonNode cutset) {
        if (!text.isTextual()) {
            return NullNode.getInstance();
        }
        if (!cutset.isTextual()) {
            return TextNode.valueOf(text.textValue().trim());
        }
        return TextNode.valueOf(StringUtils.trim(text.textValue(), cutset.textValue()));
    }

    @ScriptFunction(name = "trimLeft", params = {
            @ScriptParam("text"),
            @ScriptParam(value = "cutset", optional = true, defaultValue = "null")
    })
    public static JsonNode trimLeft(JsonNode text, JsonNode cutset) {
        if (!text.isTextual()) {
            return NullNode.getInstance();
        }
        if (!cutset.isTextual()) {
            return TextNode.valueOf(StringUtils.trimLeftEmpty(text.textValue()));
        }
        return TextNode.valueOf(StringUtils.trimLeft(text.textValue(), cutset.textValue()));
    }

    @ScriptFunction(name = "trimRight", params = {
            @ScriptParam("text"),
            @ScriptParam(value = "cutset", optional = true, defaultValue = "null")
    })
    public static JsonNode trimRight(JsonNode text, JsonNode cutset) {
        if (!text.isTextual()) {
            return NullNode.getInstance();
        }
        if (!cutset.isTextual()) {
            return TextNode.valueOf(StringUtils.trimRightEmpty(text.textValue()));
        }
        return TextNode.valueOf(StringUtils.trimRight(text.textValue(), cutset.textValue()));
    }

    @ScriptFunction(name = "split", params = {
            @ScriptParam("text"),
            @ScriptParam(value = "separator", optional = true, defaultValue = "null")
    })
    public static JsonNode split(JsonNode text, JsonNode separator) {
        if (!text.isTextual()) {
            return NullNode.getInstance();
        }
        if (!separator.isTextual()) {
            ArrayNode arrayNode = JsonUtil.createArrayNode(1);
            arrayNode.add(text);
            return arrayNode;
        }
        String[] strings = StringUtils.split(text.textValue(), separator.textValue());
        ArrayNode arrayNode = JsonUtil.createArrayNode(strings.length);
        for (String string : strings) {
            arrayNode.add(string);
        }
        return arrayNode;
    }

    @ScriptFunction(name = "findAll")
    public static JsonNode findAll(@ScriptParam("text") JsonNode text,
                                   @ScriptParam("regex") JsonNode regex) {
        if (!text.isTextual() || !regex.isTextual()) {
            return NullNode.getInstance();
        }
        Pattern pattern = Pattern.compile(regex.textValue());
        Matcher matcher = pattern.matcher(text.textValue());
        ArrayNode arrayNode = JsonUtil.createArrayNode(6);
        int s = 0;
        while (matcher.find(s)) {
            arrayNode.add(matcher.group());
            s = matcher.end();
        }
        return arrayNode;
    }

    @ScriptFunction(name = "contains")
    public static JsonNode contains(@ScriptParam("text") JsonNode text,
                                    @ScriptParam("value") JsonNode value) {
        if (!text.isTextual() || !value.isTextual()) {
            return NullNode.getInstance();
        }
        return BooleanNode.valueOf(text.textValue().contains(value.textValue()));
    }

    @ScriptFunction(name = "contains_any")
    public static JsonNode containsAny(@ScriptParam("text") JsonNode text,
                                       @ScriptParam("chars") JsonNode chars) {
        if (!text.isTextual() || !chars.isTextual()) {
            return NullNode.getInstance();
        }
        return BooleanNode.valueOf(StringUtils.containsAny(text.textValue(), CharArrUtil.toCharArr(chars.textValue())));
    }

    @ScriptFunction(name = "index")
    public static JsonNode index(@ScriptParam("text") JsonNode text,
                                 @ScriptParam("value") JsonNode value) {
        if (!text.isTextual() || !value.isTextual()) {
            return NullNode.getInstance();
        }
        return IntNode.valueOf(text.textValue().indexOf(value.textValue()));
    }

    @ScriptFunction(name = "indexAny")
    public static JsonNode indexAny(@ScriptParam("text") JsonNode text,
                                    @ScriptParam("chars") JsonNode chars) {
        if (!text.isTextual() || !chars.isTextual()) {
            return NullNode.getInstance();
        }
        return IntNode.valueOf(StringUtils.indexOfAny(text.textValue(), CharArrUtil.toCharArr(chars.textValue())));
    }

    @ScriptFunction(name = "lastIndex")
    public static JsonNode lastIndex(@ScriptParam("text") JsonNode text,
                                     @ScriptParam("value") JsonNode value) {
        if (!text.isTextual() || !value.isTextual()) {
            return NullNode.getInstance();
        }
        return IntNode.valueOf(text.textValue().lastIndexOf(value.textValue()));
    }

    @ScriptFunction(name = "lastIndexAny")
    public static JsonNode lastIndexAny(@ScriptParam("text") JsonNode text,
                                        @ScriptParam("chars") JsonNode chars) {
        if (!text.isTextual() || !chars.isTextual()) {
            return NullNode.getInstance();
        }
        String src = text.textValue();
        for (char c : CharArrUtil.toCharArr(chars.textValue())) {
            int i = src.lastIndexOf(c);
            if (i >= 0) {
                return IntNode.valueOf(i);
            }
        }
        return IntNode.valueOf(-1);
    }

    @ScriptFunction(name = "repeat")
    public static JsonNode repeat(@ScriptParam("text") JsonNode text,
                                  @ScriptParam("count") JsonNode count) {
        if (!text.isTextual() || !count.isNumber()) {
            return NullNode.getInstance();
        }
        String s = text.textValue();
        int i = count.intValue();
        if (i < 0) {
            return NullNode.getInstance();
        }
        if (i == 0 || s.isEmpty()) {
            return TextNode.EMPTY_STRING_NODE;
        }
        if (i == 1) {
            return text;
        }
        StringBuilder sb = new StringBuilder(i * s.length());
        for (int j = 0; j < i; j++) {
            sb.append(s);
        }
        return TextNode.valueOf(sb.toString());
    }

    @ScriptFunction(name = "match")
    public static JsonNode match(@ScriptParam("text") JsonNode text,
                                 @ScriptParam("regex") JsonNode regex) {
        if (!text.isTextual() || !regex.isTextual()) {
            return BooleanNode.FALSE;
        }
        return BooleanNode.valueOf(text.textValue().matches(regex.textValue()));
    }

    @ScriptFunction(name = "substring", params = {
            @ScriptParam("text"),
            @ScriptParam(value = "start", optional = true, defaultValue = "0"),
            @ScriptParam(value = "end", optional = true, defaultValue = "2147483647")
    })
    public static JsonNode substring(JsonNode text, JsonNode start, JsonNode end) {
        if (!text.isTextual()) {
            return NullNode.getInstance();
        }
        String txt = text.textValue();
        int i = start.asInt();
        if (i >= txt.length()) {
            return TextNode.EMPTY_STRING_NODE;
        }
        if (i < 0) {
            i = 0;
        }
        int j = end.asInt();
        if (j <= i) {
            return TextNode.EMPTY_STRING_NODE;
        }
        if (j >= txt.length()) {
            return i == 0 ? text : TextNode.valueOf(txt.substring(i));
        }
        return TextNode.valueOf(txt.substring(i, j));
    }

    @ScriptFunction(name = "toString")
    public static JsonNode toStringNoArg() {
        return TextNode.EMPTY_STRING_NODE;
    }

    @ScriptFunction(name = "toString")
    public static JsonNode toStringValue(@ScriptParam("value") JsonNode value) {
        return JsonUtil.isNull(value) ? NULL_TEXT : TextNode.valueOf(JsonUtil.toString(value));
    }
}
