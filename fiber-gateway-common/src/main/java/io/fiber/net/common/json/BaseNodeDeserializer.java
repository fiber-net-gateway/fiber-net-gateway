package io.fiber.net.common.json;


import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.fiber.net.common.utils.JsonUtil;

import java.io.IOException;

/**
 * Base class for all actual {@link JsonNode} deserializer
 * implementations
 */
@SuppressWarnings("serial")
abstract class BaseNodeDeserializer<T extends JsonNode>
        extends StdDeserializer<T> {
    
    protected final Boolean _supportsUpdates;

    public BaseNodeDeserializer(Class<T> vc, Boolean supportsUpdates) {
        super(vc);
        _supportsUpdates = supportsUpdates;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
                                      TypeDeserializer typeDeserializer)
            throws IOException {
        // Output can be as JSON Object, Array or scalar: no way to know a priori:
        return typeDeserializer.deserializeTypedFromAny(p, ctxt);
    }

    @Override // since 2.12
    public LogicalType logicalType() {
        return LogicalType.Untyped;
    }

    // 07-Nov-2014, tatu: When investigating [databind#604], realized that it makes
    //   sense to also mark this is cachable, since lookup not exactly free, and
    //   since it's not uncommon to "read anything"
    @Override
    public boolean isCachable() {
        return true;
    }

    @Override // since 2.9
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _supportsUpdates;
    }

    /*
    /**********************************************************
    /* Overridable methods
    /**********************************************************
     */

    /**
     * Method called when there is a duplicate value for a field.
     * By default we don't care, and the last value is used.
     * Can be overridden to provide alternate handling, such as throwing
     * an exception, or choosing different strategy for combining values
     * or choosing which one to keep.
     *
     * @param fieldName  Name of the field for which duplicate value was found
     * @param objectNode Object node that contains values
     * @param oldValue   Value that existed for the object node before newValue
     *                   was added
     * @param newValue   Newly added value just added to the object node
     */
    protected void _handleDuplicateField(JsonParser p, DeserializationContext ctxt,
                                         JsonNodeFactory nodeFactory,
                                         String fieldName, ObjectNode objectNode,
                                         JsonNode oldValue, JsonNode newValue)
            throws JsonProcessingException {
        // [databind#237]: Report an error if asked to do so:
        if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)) {
            // 11-Sep-2019, tatu: Can not pass "property name" because we may be
            //    missing enclosing JSON content context...
// ctxt.reportPropertyInputMismatch(JsonNode.class, fieldName,
            ctxt.reportInputMismatch(JsonNode.class,
                    "Duplicate field '%s' for `ObjectNode`: not allowed when `DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY` enabled",
                    fieldName);
        }
        // [databind#2732]: Special case for XML; automatically coerce into `ArrayNode`
        if (ctxt.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES)) {
            // Note that ideally we wouldn't have to shuffle things but... Map.putIfAbsent()
            // only added in JDK 8, to efficiently check for add. So...
            if (oldValue.isArray()) { // already was array, to append
                ((ArrayNode) oldValue).add(newValue);
                objectNode.replace(fieldName, oldValue);
            } else { // was not array, convert
                ArrayNode arr = nodeFactory.arrayNode();
                arr.add(oldValue);
                arr.add(newValue);
                objectNode.replace(fieldName, arr);
            }
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    /**
     * Method called to deserialize Object node instance when there is no existing
     * node to modify.
     */
    protected final ObjectNode deserializeObject(JsonParser p, DeserializationContext ctxt,
                                                 final JsonNodeFactory nodeFactory) throws IOException {
        final ObjectNode node = nodeFactory.objectNode();
        String key = p.nextFieldName();
        for (; key != null; key = p.nextFieldName()) {
            JsonNode value;
            JsonToken t = p.nextToken();
            if (t == null) { // can this ever occur?
                t = JsonToken.NOT_AVAILABLE; // can this ever occur?
            }
            switch (t.id()) {
                case JsonTokenId.ID_START_OBJECT:
                    value = deserializeObject(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    value = deserializeArray(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    value = _fromEmbedded(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_STRING:
                    value = nodeFactory.textNode(p.getText());
                    break;
                case JsonTokenId.ID_NUMBER_INT:
                    value = _fromInt(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_TRUE:
                    value = nodeFactory.booleanNode(true);
                    break;
                case JsonTokenId.ID_FALSE:
                    value = nodeFactory.booleanNode(false);
                    break;
                case JsonTokenId.ID_NULL:
                    value = nodeFactory.nullNode();
                    break;
                default:
                    value = deserializeAny(p, ctxt, nodeFactory);
            }
            JsonNode old = node.replace(key, value);
            if (old != null) {
                _handleDuplicateField(p, ctxt, nodeFactory,
                        key, node, old, value);
            }
        }
        return node;
    }

    /**
     * Alternate deserialization method used when parser already points to first
     * FIELD_NAME and not START_OBJECT.
     *
     * @since 2.9
     */
    protected final ObjectNode deserializeObjectAtName(JsonParser p, DeserializationContext ctxt,
                                                       final JsonNodeFactory nodeFactory) throws IOException {
        final ObjectNode node = nodeFactory.objectNode();
        String key = p.currentName();
        for (; key != null; key = p.nextFieldName()) {
            JsonNode value;
            JsonToken t = p.nextToken();
            if (t == null) { // can this ever occur?
                t = JsonToken.NOT_AVAILABLE; // can this ever occur?
            }
            switch (t.id()) {
                case JsonTokenId.ID_START_OBJECT:
                    value = deserializeObject(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    value = deserializeArray(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    value = _fromEmbedded(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_STRING:
                    value = nodeFactory.textNode(p.getText());
                    break;
                case JsonTokenId.ID_NUMBER_INT:
                    value = _fromInt(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_TRUE:
                    value = nodeFactory.booleanNode(true);
                    break;
                case JsonTokenId.ID_FALSE:
                    value = nodeFactory.booleanNode(false);
                    break;
                case JsonTokenId.ID_NULL:
                    value = nodeFactory.nullNode();
                    break;
                default:
                    value = deserializeAny(p, ctxt, nodeFactory);
            }
            JsonNode old = node.replace(key, value);
            if (old != null) {
                _handleDuplicateField(p, ctxt, nodeFactory,
                        key, node, old, value);
            }
        }
        return node;
    }

    /**
     * Alternate deserialization method that is to update existing {@link ObjectNode}
     * if possible.
     *
     * @since 2.9
     */
    protected final JsonNode updateObject(JsonParser p, DeserializationContext ctxt,
                                          final ObjectNode node) throws IOException {
        String key;
        if (p.isExpectedStartObjectToken()) {
            key = p.nextFieldName();
        } else {
            if (!p.hasToken(JsonToken.FIELD_NAME)) {
                return deserialize(p, ctxt);
            }
            key = p.currentName();
        }
        for (; key != null; key = p.nextFieldName()) {
            // If not, fall through to regular handling
            JsonToken t = p.nextToken();

            // First: see if we can merge things:
            JsonNode old = node.get(key);
            if (old != null) {
                if (old instanceof ObjectNode) {
                    // [databind#3056]: merging only if had Object and
                    // getting an Object
                    if (t == JsonToken.START_OBJECT) {
                        JsonNode newValue = updateObject(p, ctxt, (ObjectNode) old);
                        if (newValue != old) {
                            node.set(key, newValue);
                        }
                        continue;
                    }
                } else if (old instanceof ArrayNode) {
                    // [databind#3056]: related to Object handling, ensure
                    // Array values also match for mergeability
                    if (t == JsonToken.START_ARRAY) {
                        JsonNode newValue = updateArray(p, ctxt, (ArrayNode) old);
                        if (newValue != old) {
                            node.set(key, newValue);
                        }
                        continue;
                    }
                }
            }
            if (t == null) { // can this ever occur?
                t = JsonToken.NOT_AVAILABLE;
            }
            JsonNode value;
            JsonNodeFactory nodeFactory = JsonNodeFactory.INSTANCE;
            switch (t.id()) {
                case JsonTokenId.ID_START_OBJECT:
                    value = deserializeObject(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    value = deserializeArray(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    value = _fromEmbedded(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_STRING:
                    value = nodeFactory.textNode(p.getText());
                    break;
                case JsonTokenId.ID_NUMBER_INT:
                    value = _fromInt(p, ctxt, nodeFactory);
                    break;
                case JsonTokenId.ID_TRUE:
                    value = nodeFactory.booleanNode(true);
                    break;
                case JsonTokenId.ID_FALSE:
                    value = nodeFactory.booleanNode(false);
                    break;
                case JsonTokenId.ID_NULL:
                    value = nodeFactory.nullNode();
                    break;
                default:
                    value = deserializeAny(p, ctxt, nodeFactory);
            }
            // 15-Feb-2021, tatu: I don't think this should have been called
            //   on update case (was until 2.12.2) and was simply result of
            //   copy-paste.
            /*
            if (old != null) {
                _handleDuplicateField(p, ctxt, nodeFactory,
                        key, node, old, value);
            }
            */
            node.set(key, value);
        }
        return node;
    }

    protected final ArrayNode deserializeArray(JsonParser p, DeserializationContext ctxt,
                                               final JsonNodeFactory nodeFactory) throws IOException {
        ArrayNode node = nodeFactory.arrayNode();
        JsonToken t;

        while ((t = p.nextToken()) != null) {
            switch (t.id()) {
                case JsonTokenId.ID_START_OBJECT:
                    node.add(deserializeObject(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    node.add(deserializeArray(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_END_ARRAY:
                    return node;
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    node.add(_fromEmbedded(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_STRING:
                    node.add(nodeFactory.textNode(p.getText()));
                    break;
                case JsonTokenId.ID_NUMBER_INT:
                    node.add(_fromInt(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_TRUE:
                    node.add(nodeFactory.booleanNode(true));
                    break;
                case JsonTokenId.ID_FALSE:
                    node.add(nodeFactory.booleanNode(false));
                    break;
                case JsonTokenId.ID_NULL:
                    node.add(nodeFactory.nullNode());
                    break;
                default:
                    node.add(deserializeAny(p, ctxt, nodeFactory));
                    break;
            }
        }
        // should never really get here
        return node;
    }

    /**
     * Alternate deserialization method that is to update existing {@link ObjectNode}
     * if possible.
     *
     * @since 2.9
     */
    protected final JsonNode updateArray(JsonParser p, DeserializationContext ctxt,
                                         final ArrayNode node) throws IOException {
        final JsonNodeFactory nodeFactory = JsonNodeFactory.INSTANCE;
        while (true) {
            JsonToken t = p.nextToken();
            switch (t.id()) {
                case JsonTokenId.ID_START_OBJECT:
                    node.add(deserializeObject(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    node.add(deserializeArray(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_END_ARRAY:
                    return node;
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    node.add(_fromEmbedded(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_STRING:
                    node.add(nodeFactory.textNode(p.getText()));
                    break;
                case JsonTokenId.ID_NUMBER_INT:
                    node.add(_fromInt(p, ctxt, nodeFactory));
                    break;
                case JsonTokenId.ID_TRUE:
                    node.add(nodeFactory.booleanNode(true));
                    break;
                case JsonTokenId.ID_FALSE:
                    node.add(nodeFactory.booleanNode(false));
                    break;
                case JsonTokenId.ID_NULL:
                    node.add(nodeFactory.nullNode());
                    break;
                default:
                    node.add(deserializeAny(p, ctxt, nodeFactory));
                    break;
            }
        }
    }

    protected final JsonNode deserializeAny(JsonParser p, DeserializationContext ctxt,
                                            final JsonNodeFactory nodeFactory) throws IOException {
        switch (p.currentTokenId()) {
            case JsonTokenId.ID_END_OBJECT: // for empty JSON Objects we may point to this?
                return nodeFactory.objectNode();
            case JsonTokenId.ID_FIELD_NAME:
                return deserializeObjectAtName(p, ctxt, nodeFactory);
            case JsonTokenId.ID_EMBEDDED_OBJECT:
                return _fromEmbedded(p, ctxt, nodeFactory);
            case JsonTokenId.ID_STRING:
                return nodeFactory.textNode(p.getText());
            case JsonTokenId.ID_NUMBER_INT:
                return _fromInt(p, ctxt, nodeFactory);
            case JsonTokenId.ID_NUMBER_FLOAT:
                return _fromFloat(p, ctxt, nodeFactory);
            case JsonTokenId.ID_TRUE:
                return nodeFactory.booleanNode(true);
            case JsonTokenId.ID_FALSE:
                return nodeFactory.booleanNode(false);
            case JsonTokenId.ID_NULL:
                return nodeFactory.nullNode();

            /* Caller checks for these, should not get here ever
        case JsonTokenId.ID_START_OBJECT:
            return deserializeObject(p, ctxt, nodeFactory);
        case JsonTokenId.ID_START_ARRAY:
            return deserializeArray(p, ctxt, nodeFactory);
            */


            // These states cannot be mapped; input stream is
            // off by an event or two

            //case END_OBJECT:
            //case END_ARRAY:
            default:
        }
        return (JsonNode) ctxt.handleUnexpectedToken(handledType(), p);
    }

    protected final JsonNode _fromInt(JsonParser p, DeserializationContext ctxt,
                                      JsonNodeFactory nodeFactory) throws IOException {
        JsonParser.NumberType nt;
        int feats = ctxt.getDeserializationFeatures();
        if ((feats & F_MASK_INT_COERCIONS) != 0) {
            if (DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.enabledIn(feats)) {
                nt = JsonParser.NumberType.BIG_INTEGER;
            } else if (DeserializationFeature.USE_LONG_FOR_INTS.enabledIn(feats)) {
                nt = JsonParser.NumberType.LONG;
            } else {
                nt = p.getNumberType();
            }
        } else {
            nt = p.getNumberType();
        }
        if (nt == JsonParser.NumberType.INT) {
            return nodeFactory.numberNode(p.getIntValue());
        }
        if (nt == JsonParser.NumberType.LONG) {
            return nodeFactory.numberNode(p.getLongValue());
        }
        return nodeFactory.numberNode(p.getBigIntegerValue());
    }

    protected final JsonNode _fromFloat(JsonParser p, DeserializationContext ctxt,
                                        final JsonNodeFactory nodeFactory) throws IOException {
        JsonParser.NumberType nt = p.getNumberType();
        if (nt == JsonParser.NumberType.BIG_DECIMAL) {
            return nodeFactory.numberNode(p.getDecimalValue());
        }
        if (ctxt.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            // 20-May-2016, tatu: As per [databind#1028], need to be careful
            //   (note: JDK 1.8 would have `Double.isFinite()`)
            if (p.isNaN()) {
                return nodeFactory.numberNode(p.getDoubleValue());
            }
            return nodeFactory.numberNode(p.getDecimalValue());
        }
        if (nt == JsonParser.NumberType.FLOAT) {
            return nodeFactory.numberNode(p.getFloatValue());
        }
        return nodeFactory.numberNode(p.getDoubleValue());
    }

    protected final JsonNode _fromEmbedded(JsonParser p, DeserializationContext ctxt,
                                           JsonNodeFactory nodeFactory) throws IOException {
        Object ob = p.getEmbeddedObject();
        if (ob == null) { // should this occur?
            return nodeFactory.nullNode();
        }
        Class<?> type = ob.getClass();
        if (type == byte[].class) { // most common special case
            return nodeFactory.binaryNode((byte[]) ob);
        }
        if (ob instanceof JsonNode) {
            // [databind#433]: but could also be a JsonNode hiding in there!
            return (JsonNode) ob;
        }
        // any other special handling needed?
        return nodeFactory.nullNode();
    }
}
