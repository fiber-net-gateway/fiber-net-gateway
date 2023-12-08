package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.parse.SpelMessage;
import io.fiber.net.script.std.Compares;

public class Unary {
    public static JsonNode not(JsonNode operand){
        return BooleanNode.valueOf(!Compares.logic(operand));
    }

    public static JsonNode minus(JsonNode operand) throws ScriptExecException {
        NumericNode n = null;
        if (operand instanceof NumericNode) {
            n = (NumericNode) operand;
        } else if (operand instanceof TextNode) {
            n = AstUtils.tryToNumber(operand.asText());
        }

        if (n != null) {
            if (n instanceof DoubleNode) {
                return DoubleNode.valueOf(0 - n.doubleValue());
            } else if (n instanceof FloatNode) {
                return FloatNode.valueOf(0 - n.floatValue());
            } else if (n instanceof LongNode) {
                return LongNode.valueOf(-n.longValue());
            } else {
                return IntNode.valueOf(-n.intValue());
            }
        }

        throw new ScriptExecException(SpelMessage.OPERATOR_NOT_SUPPORTED_UNARY
                .formatMessage("-", operand.getNodeType()));
    }

    public static JsonNode plus(JsonNode operandOne) throws ScriptExecException {
        if (operandOne instanceof NumericNode) {
            return operandOne;
        }
        if (operandOne.isTextual()) {
            String value = operandOne.asText();
            NumericNode node = AstUtils.tryToNumber(value);
            if (node != null) {
                return node;
            }
        }
        throw new ScriptExecException(SpelMessage.OPERATOR_NOT_SUPPORTED_UNARY.formatMessage("+", operandOne));
    }

    public static TextNode typeof(JsonNode node) {
        return TYPEOF[node.getNodeType().ordinal()];
    }


    private static final TextNode[] TYPEOF;

    static {
        JsonNodeType[] values = JsonNodeType.values();
        TYPEOF = new TextNode[values.length];
        for (int i = 0; i < values.length; i++) {
            TYPEOF[i] = TextNode.valueOf(values[i].name().toLowerCase());
        }
    }
}
