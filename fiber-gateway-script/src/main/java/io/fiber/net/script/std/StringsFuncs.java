package io.fiber.net.script.std;

import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringsFuncs {

    private static TextNode assertTextNode(ExecutionContext context, int minLen) {
        assert minLen >= 1;
        JsonNode node;
        if (minLen > context.getArgCnt() || !(node = context.getArgVal(0)).isTextual()) {
            return null;
        }
        return (TextNode) node;
    }

    static class HasPrefixFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return BooleanNode.FALSE;
            }
            JsonNode arg = context.getArgVal(1);
            if (!arg.isTextual()) {
                return BooleanNode.FALSE;
            }
            return BooleanNode.valueOf(textNode.textValue().startsWith(arg.textValue()));
        }
    }

    static class HasSuffixFunc implements Library.Function {
        @Override
        public BooleanNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return BooleanNode.FALSE;
            }
            JsonNode arg = context.getArgVal(1);
            if (!arg.isTextual()) {
                return BooleanNode.FALSE;
            }
            return BooleanNode.valueOf(textNode.textValue().endsWith(arg.textValue()));
        }
    }

    static class ToLowerFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            return TextNode.valueOf(textNode.textValue().toLowerCase());
        }
    }

    static class ToUpperFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            return TextNode.valueOf(textNode.textValue().toUpperCase());
        }
    }

    static class TrimFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (context.getArgCnt() < 2 || !context.getArgVal(1).isTextual()) {
                return TextNode.valueOf(textNode.textValue().trim());
            }
            return TextNode.valueOf(StringUtils.trim(textNode.textValue(), context.getArgVal(1).textValue()));
        }
    }

    static class TrimLeft implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (context.getArgCnt() < 2 || !context.getArgVal(1).isTextual()) {
                return TextNode.valueOf(StringUtils.trimLeftEmpty(textNode.textValue()));
            }
            return TextNode.valueOf(StringUtils.trimLeft(textNode.textValue(), context.getArgVal(1).textValue()));
        }
    }

    static class TrimRight implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (context.getArgCnt() < 2 || !context.getArgVal(1).isTextual()) {
                return TextNode.valueOf(StringUtils.trimRightEmpty(textNode.textValue()));
            }
            return TextNode.valueOf(StringUtils.trimRight(textNode.textValue(), context.getArgVal(1).textValue()));
        }
    }

    static class SplitFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 1);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (context.getArgCnt() < 2 || !context.getArgVal(1).isTextual()) {
                ArrayNode arrayNode = JsonUtil.createArrayNode(1);
                arrayNode.add(textNode);
                return arrayNode;
            }
            String[] strings = StringUtils.split(textNode.textValue(), context.getArgVal(1).textValue());
            ArrayNode arrayNode = JsonUtil.createArrayNode(strings.length);
            for (String string : strings) {
                arrayNode.add(string);
            }
            return arrayNode;
        }
    }

    static class FindAllFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (!context.getArgVal(1).isTextual()) {
                return NullNode.getInstance();
            }
            Pattern pattern = Pattern.compile(context.getArgVal(1).textValue());
            Matcher matcher = pattern.matcher(textNode.textValue());
            ArrayNode arrayNode = JsonUtil.createArrayNode(6);
            int s = 0;
            while (matcher.find(s)) {
                arrayNode.add(matcher.group());
                s = matcher.end();
            }
            return arrayNode;
        }
    }

    static class ContainsFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (!context.getArgVal(1).isTextual()) {
                return NullNode.getInstance();
            }
            String value = context.getArgVal(1).textValue();
            return BooleanNode.valueOf(textNode.textValue().contains(value));
        }
    }

    static class ContainsAnyFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            if (!context.getArgVal(1).isTextual()) {
                return NullNode.getInstance();
            }
            String value = context.getArgVal(1).textValue();
            return BooleanNode.valueOf(StringUtils.containsAny(textNode.textValue(), CharArrUtil.toCharArr(value)));
        }
    }

    static class IndexFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isTextual()) {
                return NullNode.getInstance();
            }
            String value = argVal.textValue();
            return IntNode.valueOf(textNode.textValue().indexOf(value));
        }
    }

    static class IndexAnyFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isTextual()) {
                return NullNode.getInstance();
            }
            String value = argVal.textValue();
            return IntNode.valueOf(StringUtils.indexOfAny(textNode.textValue(), CharArrUtil.toCharArr(value)));
        }
    }

    static class LastIndexFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isTextual()) {
                return NullNode.getInstance();
            }
            String value = argVal.textValue();
            return IntNode.valueOf(textNode.textValue().lastIndexOf(value));
        }
    }

    static class LastIndexAnyFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isTextual()) {
                return NullNode.getInstance();
            }
            String src = textNode.textValue();

            int i;
            String value = argVal.textValue();
            char[] chars = CharArrUtil.toCharArr(value);
            for (char c : chars) {
                if ((i = src.lastIndexOf(c)) >= 0) {
                    return IntNode.valueOf(i);
                }
            }
            return IntNode.valueOf(-1);
        }
    }

    static class RepeatFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isNumber()) {
                return NullNode.getInstance();
            }
            String s = textNode.textValue();
            int i = argVal.intValue();

            if (i < 0) {
                return NullNode.getInstance();
            } else if (i == 0 || s.isEmpty()) {
                return TextNode.valueOf("");
            } else if (i == 1) {
                return textNode;
            } else {
                StringBuilder sb = new StringBuilder(i * s.length());
                for (int j = 0; j < i; j++) {
                    sb.append(s);
                }
                return TextNode.valueOf(sb.toString());
            }
        }
    }

    static class MatchFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) {
            TextNode textNode = assertTextNode(context, 2);
            if (textNode == null) {
                return NullNode.getInstance();
            }
            JsonNode argVal = context.getArgVal(1);
            if (!argVal.isTextual()) {
                return NullNode.getInstance();
            }

            return BooleanNode.valueOf(textNode.textValue().matches(argVal.textValue()));
        }
    }

    static class SubstringFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) {
            JsonNode node;
            if (context.noArgs() || !(node = context.getArgVal(0)).isTextual()) {
                return NullNode.getInstance();
            }
            String txt = node.textValue();
            switch (context.getArgCnt()) {
                case 1:
                    return node;
                case 2: {
                    int i = context.getArgVal(1).asInt();
                    if (i <= 0) {
                        return node;
                    }
                    if (i >= txt.length()) {
                        return TextNode.valueOf("");
                    }
                    return TextNode.valueOf(txt.substring(i));
                }
                default: {
                    int i = context.getArgVal(1).asInt();
                    if (i >= txt.length()) {
                        return TextNode.valueOf("");
                    }
                    if (i < 0) {
                        i = 0;
                    }
                    int j = context.getArgVal(2).asInt();
                    if (j <= i) {
                        return TextNode.valueOf("");
                    }
                    if (j >= txt.length()) {
                        return TextNode.valueOf(txt.substring(i));
                    }
                    return TextNode.valueOf(txt.substring(i, j));
                }
            }
        }
    }

    static class ToStringFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) {
            if (context.noArgs()) {
                return null;
            }
            JsonNode arg = context.getArgVal(0);
            if (JsonUtil.isNull(arg)) {
                return TextNode.valueOf("null");
            } else {
                return TextNode.valueOf(JsonUtil.toString(arg));
            }
        }
    }


    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("strings.hasPrefix", new HasPrefixFunc());
        FUNC.put("strings.hasSuffix", new HasSuffixFunc());
        FUNC.put("strings.toLower", new ToLowerFunc());
        FUNC.put("strings.toUpper", new ToUpperFunc());
        FUNC.put("strings.trim", new TrimFunc());
        FUNC.put("strings.trimLeft", new TrimLeft());
        FUNC.put("strings.trimRight", new TrimRight());
        FUNC.put("strings.split", new SplitFunc());
        FUNC.put("strings.findAll", new FindAllFunc());
        FUNC.put("strings.contains", new ContainsFunc());
        FUNC.put("strings.contains_any", new ContainsAnyFunc());
        FUNC.put("strings.index", new IndexFunc());
        FUNC.put("strings.indexAny", new IndexAnyFunc());
        FUNC.put("strings.lastIndex", new LastIndexFunc());
        FUNC.put("strings.lastIndexAny", new LastIndexAnyFunc());
        FUNC.put("strings.repeat", new RepeatFunc());
        FUNC.put("strings.match", new MatchFunc());
        FUNC.put("strings.substring", new SubstringFunc());
        FUNC.put("strings.toString", new ToStringFunc());
    }
}
