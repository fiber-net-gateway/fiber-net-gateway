package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.Library;
import io.fiber.net.script.ExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringsFuncs {

    private static TextNode assertTextNode(JsonNode[] args, int minLen) {
        assert minLen >= 1;
        if (args == null || minLen > args.length || !args[0].isTextual()) {
            return null;
        }
        return (TextNode) args[0];
    }

    static class HasPrefixFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, BooleanNode.FALSE);
                return;
            }
            JsonNode arg = args[1];
            if (!arg.isTextual()) {
                context.returnVal(this, BooleanNode.FALSE);
                return;
            }
            context.returnVal(this, BooleanNode.valueOf(textNode.textValue().startsWith(arg.textValue())));
        }
    }

    static class HasSuffixFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, BooleanNode.FALSE);
                return;
            }
            JsonNode arg = args[1];
            if (!arg.isTextual()) {
                context.returnVal(this, BooleanNode.FALSE);
                return;
            }
            context.returnVal(this, BooleanNode.valueOf(textNode.textValue().endsWith(arg.textValue())));
        }
    }

    static class ToLowerFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            context.returnVal(this, TextNode.valueOf(textNode.textValue().toLowerCase()));
        }
    }

    static class ToUpperFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            context.returnVal(this, TextNode.valueOf(textNode.textValue().toUpperCase()));
        }
    }

    static class TrimFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (args.length < 2 || !args[1].isTextual()) {
                context.returnVal(this, TextNode.valueOf(textNode.textValue().trim()));
                return;
            }
            context.returnVal(this, TextNode.valueOf(StringUtils.trim(textNode.textValue(), args[1].textValue())));
        }
    }

    static class TrimLeft implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (args.length < 2 || !args[1].isTextual()) {
                context.returnVal(this, TextNode.valueOf(StringUtils.trimLeftEmpty(textNode.textValue())));
                return;
            }
            context.returnVal(this, TextNode.valueOf(StringUtils.trimLeft(textNode.textValue(), args[1].textValue())));
        }
    }

    static class TrimRight implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (args.length < 2 || !args[1].isTextual()) {
                context.returnVal(this, TextNode.valueOf(StringUtils.trimRightEmpty(textNode.textValue())));
                return;
            }
            context.returnVal(this, TextNode.valueOf(StringUtils.trimRight(textNode.textValue(), args[1].textValue())));
        }
    }

    static class SplitFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 1);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (args.length < 2 || !args[1].isTextual()) {
                ArrayNode arrayNode = JsonUtil.createArrayNode(1);
                arrayNode.add(textNode);
                context.returnVal(this, arrayNode);
                return;
            }
            String[] strings = StringUtils.split(textNode.textValue(), args[1].textValue());
            ArrayNode arrayNode = JsonUtil.createArrayNode(strings.length);
            for (String string : strings) {
                arrayNode.add(string);
            }
            context.returnVal(this, arrayNode);
        }
    }

    static class FindAllFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            Pattern pattern = Pattern.compile(textNode.textValue());
            Matcher matcher = pattern.matcher(value);
            ArrayNode arrayNode = JsonUtil.createArrayNode(6);
            int s = 0;
            while (matcher.find(s)) {
                arrayNode.add(matcher.group());
                s = matcher.end();
            }
            context.returnVal(this, arrayNode);
        }
    }

    static class ContainsFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            context.returnVal(this, BooleanNode.valueOf(textNode.textValue().contains(value)));
        }
    }

    static class ContainsAnyFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            context.returnVal(this, BooleanNode.valueOf(StringUtils.containsAny(textNode.textValue(), CharArrUtil.toCharArr(value))));
        }
    }

    static class IndexFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            context.returnVal(this, IntNode.valueOf(textNode.textValue().indexOf(value)));
        }
    }

    static class IndexAnyFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            context.returnVal(this, IntNode.valueOf(StringUtils.indexOfAny(textNode.textValue(), CharArrUtil.toCharArr(value))));
        }
    }

    static class LastIndexFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String value = args[1].textValue();
            context.returnVal(this, IntNode.valueOf(textNode.textValue().lastIndexOf(value)));
        }
    }

    static class LastIndexAnyFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String src = textNode.textValue();

            int i;
            String value = args[1].textValue();
            char[] chars = CharArrUtil.toCharArr(value);
            for (char c : chars) {
                if ((i = src.lastIndexOf(c)) >= 0) {
                    context.returnVal(this, IntNode.valueOf(i));
                    return;
                }
            }
            context.returnVal(this, IntNode.valueOf(-1));
        }
    }

    static class RepeatFunc implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isNumber()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String s = textNode.textValue();
            int i = args[1].intValue();

            if (i < 0) {
                context.returnVal(this, NullNode.getInstance());
            } else if (i == 0 || s.isEmpty()) {
                context.returnVal(this, TextNode.valueOf(""));
            } else if (i == 1) {
                context.returnVal(this, textNode);
            } else {
                StringBuilder sb = new StringBuilder(i * s.length());
                for (int j = 0; j < i; j++) {
                    sb.append(s);
                }
                context.returnVal(this, TextNode.valueOf(sb.toString()));
            }
        }
    }

    static class MatchFunc implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            TextNode textNode = assertTextNode(args, 2);
            if (textNode == null) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            if (!args[1].isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }

            context.returnVal(this, BooleanNode.valueOf(args[1].textValue().matches(textNode.textValue())));
        }
    }

    static class SubstringFunc implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            JsonNode node;
            if (ArrayUtils.isEmpty(args) || !(node = args[0]).isTextual()) {
                context.returnVal(this, NullNode.getInstance());
                return;
            }
            String txt = node.textValue();
            switch (args.length) {
                case 1:
                    context.returnVal(this, node);
                    return;
                case 2: {
                    int i = args[1].asInt();
                    if (i <= 0) {
                        context.returnVal(this, node);
                        return;
                    }
                    if (i >= txt.length()) {
                        context.returnVal(this, TextNode.valueOf(""));
                        return;
                    }
                    context.returnVal(this, TextNode.valueOf(txt.substring(i)));
                    return;
                }
                default: {
                    int i = args[1].asInt();
                    if (i >= txt.length()) {
                        context.returnVal(this, TextNode.valueOf(""));
                        return;
                    }
                    if (i < 0) {
                        i = 0;
                    }
                    int j = args[2].asInt();
                    if (j <= i) {
                        context.returnVal(this, TextNode.valueOf(""));
                        return;
                    }
                    if (j >= txt.length()) {
                        context.returnVal(this, TextNode.valueOf(txt.substring(i)));
                        return;
                    }
                    context.returnVal(this, TextNode.valueOf(txt.substring(i, j)));
                }
            }
        }
    }

    static class ToStringFunc implements Library.Function {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            if (ArrayUtils.isEmpty(args)) {
                context.returnVal(this, null);
                return;
            }
            JsonNode arg = args[0];
            if (JsonUtil.isNull(arg)) {
                context.returnVal(this, TextNode.valueOf("null"));
            } else {
                context.returnVal(this, TextNode.valueOf(JsonUtil.toString(arg)));
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
        FUNC.put("strings.join", new ArrayFuncs.ArrayJoinFunc());
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
