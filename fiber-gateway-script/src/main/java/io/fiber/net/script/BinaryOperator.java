package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.parse.SpelMessage;
import io.fiber.net.script.std.Compares;

import java.util.Optional;

public class BinaryOperator {
    public static JsonNode modulo(JsonNode operandOne, JsonNode operandTwo) throws ScriptExecException {
        if (operandOne instanceof NumericNode && operandTwo instanceof NumericNode) {
            try {
                NumericNode op1 = (NumericNode) operandOne;
                NumericNode op2 = (NumericNode) operandTwo;
                if (op1 instanceof IntNode || op2 instanceof IntNode) {
                    return IntNode.valueOf(op1.intValue() % op2.intValue());
                } else if (op1 instanceof FloatNode || op2 instanceof FloatNode) {
                    return FloatNode.valueOf(op1.floatValue() % op2.floatValue());
                } else if (op1 instanceof LongNode || op2 instanceof LongNode) {
                    return LongNode.valueOf(op1.longValue() % op2.longValue());
                } else {
                    return DoubleNode.valueOf(op1.doubleValue() % op2.doubleValue());
                }
            } catch (RuntimeException e) {
                throw new ScriptExecException(
                        SpelMessage.COMPUTE_ERROR.formatMessage(
                                operandOne,
                                "%",
                                operandTwo));
            }
        }
        throw new ScriptExecException(
                SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES.formatMessage(
                        "%",
                        String.valueOf(operandOne.getNodeType()),
                        String.valueOf(Optional.ofNullable(operandTwo)
                                .map(JsonNode::getNodeType).orElse(JsonNodeType.NULL))));
    }


    public static JsonNode divide(JsonNode operandOne, JsonNode operandTwo) throws ScriptExecException {
        if (operandOne instanceof NumericNode && operandTwo instanceof NumericNode) {
            try {
                NumericNode op1 = (NumericNode) operandOne;
                NumericNode op2 = (NumericNode) operandTwo;
                if (op1 instanceof IntNode || op2 instanceof IntNode) {
                    return IntNode.valueOf(op1.asInt() / op2.asInt());
                } else if (op1 instanceof LongNode || op2 instanceof LongNode) {
                    return LongNode.valueOf(op1.asLong() / op2.asLong());
                } else if (op1 instanceof FloatNode || op2 instanceof FloatNode) {
                    return FloatNode.valueOf((float) (op1.asDouble() / op2.asDouble()));
                } else {
                    return DoubleNode.valueOf(op1.doubleValue() / op2.doubleValue());
                }
            } catch (RuntimeException e) {
                throw new ScriptExecException(
                        SpelMessage.COMPUTE_ERROR.formatMessage(
                                operandOne,
                                "*",
                                operandTwo));
            }
        }
        throw new ScriptExecException("cannot divide between " + operandOne + " between " + operandTwo);
    }

    public static JsonNode multiply(JsonNode left, JsonNode right) throws ScriptExecException {
        NumericNode op1 = null;
        NumericNode op2 = null;
        if (left instanceof NumericNode) {
            op1 = (NumericNode) left;
        } else if (left instanceof TextNode) {
            op1 = AstUtils.tryToNumber(left.asText());
        }
        if (right instanceof NumericNode) {
            op2 = (NumericNode) right;
        } else if (right instanceof TextNode) {
            op2 = AstUtils.tryToNumber(right.asText());
        }
        if (op1 != null && op2 != null) {
            if (op1 instanceof IntNode && op2 instanceof IntNode) {
                return IntNode.valueOf(op1.intValue() * op2.intValue());
            } else if (op1 instanceof FloatNode || op2 instanceof FloatNode) {
                return FloatNode.valueOf(op1.floatValue() * op2.floatValue());
            } else if (op1 instanceof LongNode && op2 instanceof LongNode) {
                return LongNode.valueOf(op1.longValue() * op2.longValue());
            } else if (op1 instanceof IntNode && op2 instanceof LongNode
                    || op1 instanceof LongNode && op2 instanceof IntNode) {
                return LongNode.valueOf(op1.longValue() * op2.longValue());
            } else {
                return DoubleNode.valueOf(op1.doubleValue() * op2.doubleValue());
            }
        }
        throw new ScriptExecException(SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES
                .formatMessage(
                        "*", left.getNodeType(), right.getNodeType()
                ));
    }

    public static JsonNode minus(JsonNode left, JsonNode right) throws ScriptExecException {
        NumericNode op1 = null;
        NumericNode op2 = null;
        if (left instanceof NumericNode) {
            op1 = (NumericNode) left;
        } else if (left instanceof TextNode) {
            op1 = AstUtils.tryToNumber(left.asText());
        }
        if (right instanceof NumericNode) {
            op2 = (NumericNode) right;
        } else if (right instanceof TextNode) {
            op2 = AstUtils.tryToNumber(right.asText());
        }
        if (op1 != null && op2 != null) {
            if (op1 instanceof IntNode || op2 instanceof IntNode) {
                return IntNode.valueOf(op1.intValue() - op2.intValue());
            } else if (op1 instanceof FloatNode || op2 instanceof FloatNode) {
                return FloatNode.valueOf(op1.floatValue() - op2.floatValue());
            } else if (op1 instanceof LongNode || op2 instanceof LongNode) {
                return LongNode.valueOf(op1.longValue() - op2.longValue());
            } else {
                return DoubleNode.valueOf(op1.doubleValue() - op2.doubleValue());
            }
        }
        throw new ScriptExecException(SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES
                .formatMessage("-", left.getNodeType(), right.getNodeType()));
    }

    public static JsonNode plus(JsonNode operandOne, JsonNode operandTwo) throws ScriptExecException {
        if (operandOne instanceof NumericNode && operandTwo instanceof NumericNode) {
            NumericNode op1 = (NumericNode) operandOne;
            NumericNode op2 = (NumericNode) operandTwo;
            if (op1 instanceof IntNode || op2 instanceof IntNode) {
                return IntNode.valueOf(op1.intValue() + op2.intValue());
            } else if (op1 instanceof FloatNode || op2 instanceof FloatNode) {
                return FloatNode.valueOf(op1.floatValue() + op2.floatValue());
            } else if (op1 instanceof LongNode || op2 instanceof LongNode) {
                return LongNode.valueOf(op1.longValue() + op2.longValue());
            } else { // TODO what about overflow?
                return DoubleNode.valueOf(op1.doubleValue() + op2.doubleValue());
            }
        } else if (operandOne instanceof TextNode || operandTwo instanceof TextNode) {
            StringBuilder sb = new StringBuilder();
            if (operandOne.isContainerNode() || operandOne.isPojo()) {
                sb.append("[object ");
                sb.append(operandOne.getNodeType());
                sb.append(']');
            } else {
                sb.append(operandOne.asText());
            }
            if (operandTwo.isContainerNode() || operandTwo.isPojo()) {
                sb.append("[object ");
                sb.append(operandTwo.getNodeType());
                sb.append(']');
            } else {
                sb.append(operandTwo.asText());
            }

            return TextNode.valueOf(sb.toString());
        }

        throw new ScriptExecException(
                SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES.formatMessage("+",
                        String.valueOf(operandOne.getNodeType()),
                        operandTwo));
    }

    // relation

    public static BooleanNode lt(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.lt(a, b));
    }

    public static BooleanNode gt(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.gt(a, b));
    }

    public static BooleanNode lte(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.lte(a, b));
    }

    public static BooleanNode gte(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.gte(a, b));
    }

    public static BooleanNode matches(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.matches(a, b));
    }

    public static BooleanNode seq(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.strictEqual(a, b));
    }

    public static BooleanNode eq(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.equals(a, b));
    }

    public static BooleanNode ne(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.notEquals(a, b));
    }

    public static BooleanNode sne(JsonNode a, JsonNode b) {
        return BooleanNode.valueOf(Compares.notStrictEqual(a, b));
    }

    public static BooleanNode in(JsonNode key, JsonNode obj) {
        if (obj.isArray()) {
            int i;
            return BooleanNode.valueOf(key.isIntegralNumber() && (i = key.intValue()) >= 0 && i < obj.size());
        }

        if (obj.isObject()) {
            return BooleanNode.valueOf(key.isTextual() && obj.get(key.textValue()) != null);
        }
        return BooleanNode.FALSE;
    }
}
