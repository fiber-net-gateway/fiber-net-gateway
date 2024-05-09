package io.fiber.net.script.run;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import io.fiber.net.common.json.*;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.parse.SpelMessage;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class Unaries {
    public static BooleanNode neg(JsonNode operand) {
        return BooleanNode.valueOf(Compares.neg(operand));
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

    public static IteratorNode iterate(JsonNode node) {
        if (node.isEmpty()) {
            return ValueIteratorNode.INSTANCE;
        }
        if (node instanceof ArrayNode) {
            return new ArrayIteratorNode((ArrayNode) node);
        }
        if (node instanceof ObjectNode) {
            return new ObjectIteratorNode((ObjectNode) node);
        }
        return ValueIteratorNode.INSTANCE;
    }


    public static TextNode typeof(JsonNode node) {
        return TYPEOF[node.getNodeType().ordinal()];
    }

    static class ObjectIteratorNode extends IteratorNode {

        private final ObjectNode objectNode;
        private final Iterator<Map.Entry<String, JsonNode>> fields;
        private Map.Entry<String, JsonNode> current;

        public ObjectIteratorNode(ObjectNode objectNode) {
            this.objectNode = objectNode;
            fields = objectNode.fields();
        }


        @Override
        public void serialize(JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString("<Iterator object>");
        }

        @Override
        public void serializeWithType(JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
            jgen.writeString("<Iterator object>");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObjectIteratorNode jsonNodes = (ObjectIteratorNode) o;
            return Objects.equals(objectNode, jsonNodes.objectNode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectNode);
        }

        @Override
        public boolean next() {
            if (fields.hasNext()) {
                current = fields.next();
                return true;
            }
            return false;
        }

        @Override
        public TextNode currentKey() {
            return TextNode.valueOf(current.getKey());
        }

        @Override
        public JsonNode currentValue() {
            return current.getValue();
        }
    }

    static class ValueIteratorNode extends IteratorNode {

        public static final ValueIteratorNode INSTANCE = new ValueIteratorNode();

        private ValueIteratorNode() {
        }


        @Override
        public void serialize(JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString("<Iterator value>");
        }

        @Override
        public void serializeWithType(JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
            jgen.writeString("<Iterator value>");
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public MissingNode currentKey() {
            return MissingNode.getInstance();
        }

        @Override
        public MissingNode currentValue() {
            return MissingNode.getInstance();
        }
    }

    static class ArrayIteratorNode extends IteratorNode {

        private final ArrayNode arrayNode;
        private int idx = -1;

        public ArrayIteratorNode(ArrayNode arrayNode) {
            this.arrayNode = arrayNode;
        }


        @Override
        public void serialize(JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString("<Iterator array>");
        }

        @Override
        public void serializeWithType(JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
            jgen.writeString("<Iterator array>");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayIteratorNode jsonNodes = (ArrayIteratorNode) o;
            return Objects.equals(arrayNode, jsonNodes.arrayNode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(arrayNode);
        }

        @Override
        public boolean next() {
            return ++idx < arrayNode.size();
        }

        @Override
        public IntNode currentKey() {
            return IntNode.valueOf(idx);
        }

        @Override
        public JsonNode currentValue() {
            return arrayNode.path(idx);
        }
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
