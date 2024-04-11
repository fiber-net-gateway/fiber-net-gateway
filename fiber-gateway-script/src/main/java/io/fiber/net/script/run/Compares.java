package io.fiber.net.script.run;

import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.JsonUtil;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Compares {
    public static boolean neg(JsonNode a) {
        return !logic(a);
    }

    public static boolean logic(JsonNode a) {
        if (a == null || a.isNull() || a.isMissingNode()) {
            return false;
        }

        if (a.isBoolean()) {
            return a.booleanValue();
        }

        if (a.isNumber()) {
            return a.asInt() != 0;
        }

        if (a.isTextual()) {
            return !a.asText().isEmpty();
        }

        return true;
    }

    /**
     * 等价于 js ==
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean eq(JsonNode a, JsonNode b) {

        boolean an = JsonUtil.isNull(a);
        boolean bn = JsonUtil.isNull(b);
        if (an && bn) {
            return true;
        } else if (an || bn) {
            return false;
        }

        if (a.isValueNode() && b.isValueNode()) {
            return a.asText().equals(b.asText());
        }

        return false;
    }

    /**
     * 相当于js 中的 ===
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean seq(JsonNode a, JsonNode b) {
        if (a == b) {
            return true;
        }

        if ((a == null || a.isMissingNode()) && (b == null || b.isMissingNode())) {
            return true;
        }
        if (notComparable(a, b)) return false;

        if (isIntegerNode(a)) {// a 与 b 的类型一定一样
            return a.longValue() == b.longValue();
        }

        return a.equals(b);
    }

    private static boolean isIntegerNode(JsonNode a) {
        if (a == null || !a.isNumber()) {
            return false;
        }
        return a instanceof IntNode || a instanceof LongNode || a instanceof ShortNode || a instanceof BigIntegerNode;
    }

    /**
     * !=
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean ne(JsonNode a, JsonNode b) {
        return !eq(a, b);
    }

    /**
     * !==
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean sne(JsonNode a, JsonNode b) {
        return !seq(a, b);
    }

    /**
     * >
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean gt(JsonNode a, JsonNode b) {
        if (notComparable(a, b)) return false;
        if (a.isNumber()) {
            return a.asLong() > b.asLong();
        }
        if (a.isTextual()) {
            return a.asText().compareTo(b.asText()) > 0;
        }
        return false;
    }

    /**
     * <
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean lt(JsonNode a, JsonNode b) {
        if (notComparable(a, b)) return false;
        if (a.isNumber()) {
            return a.asLong() < b.asLong();
        }
        if (a.isTextual()) {
            return a.asText().compareTo(b.asText()) < 0;
        }
        return false;
    }

    /**
     * <=
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean lte(JsonNode a, JsonNode b) {
        return !gt(a, b);
    }

    /**
     * <=
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean gte(JsonNode a, JsonNode b) {
        if (notComparable(a, b)) return false;
        if (a.isNumber()) {
            return a.asLong() >= b.asLong();
        }
        if (a.isTextual()) {
            return a.asText().compareTo(b.asText()) >= 0;
        }
        return false;
    }

    /**
     * <=
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean matches(JsonNode a, JsonNode b) {
        if (!(b instanceof TextNode)) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(b.textValue());
            return pattern.matcher(JsonUtil.toString(a)).matches();
        } catch (PatternSyntaxException ignore) {
            return false;
        }
    }


    private static boolean notComparable(JsonNode a, JsonNode b) {
        if (a == null || b == null || a.isNull() || b.isNull()) {
            return true;
        }
        if (a.getNodeType() != b.getNodeType()) {
            return true;
        }
        return a.isContainerNode();
    }

    /**
     * in
     * a in b
     * 1 in ["aa","bb",{}]
     * "bb" in {"aa":1,"bb":2}
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean in(JsonNode a, JsonNode b) {
        if (b == null || b.isValueNode()) {
            return false;
        }

        if (a == null) {
            a = NullNode.getInstance();
        }

        if (b.isArray()) {
            int k;
            return a.isInt() && (k = a.asInt()) >= 0 && k < b.size();
        }

        if (b.isObject()) {
            return a.isTextual() && b.has(a.asText());
        }
        return false;
    }

    /**
     * includes
     * "aabc" includes "ab"
     * ["a",1] includes 1
     *
     * @param a f
     * @param b s
     * @return true/false
     */
    public static boolean includes(JsonNode a, JsonNode b) {
        if (a == null) {
            return false;
        }

        if (!a.isArray()) {
            return a.isTextual() && b != null && b.isTextual() && a.textValue().contains(b.textValue());
        }

        for (JsonNode jsonNode : a) {
            if (seq(jsonNode, b)) {
                return true;
            }
        }
        return false;
    }

}
