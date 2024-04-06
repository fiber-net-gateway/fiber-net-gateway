package io.fiber.net.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.fiber.net.common.utils.JsonUtil;

import java.io.IOException;

/**
 * Deserializer that can build instances of {@link JsonNode} from any
 * JSON content, using appropriate {@link JsonNode} type.
 */
@SuppressWarnings("serial")
public class JsonNodeDeserializer
        extends BaseNodeDeserializer<JsonNode> {
    /**
     * Singleton instance of generic deserializer for {@link JsonNode}.
     * Only used for types other than JSON Object and Array.
     */
    private final static JsonNodeDeserializer instance = new JsonNodeDeserializer();

    protected JsonNodeDeserializer() {
        // `null` means that explicit "merge" is honored and may or may not work, but
        // that per-type and global defaults do not enable merging. This because
        // some node types (Object, Array) do support, others don't.
        super(JsonNode.class, null);
    }

    /**
     * Factory method for accessing deserializer for specific node type
     */
    @SuppressWarnings("unchecked")
    public static <T extends JsonNode> JsonDeserializer<T> getDeserializer(Class<T> nodeClass) {
        if (nodeClass == ObjectNode.class) {
            return (JsonDeserializer<T>) ObjectDeserializer.getInstance();
        }
        if (nodeClass == ArrayNode.class) {
            return (JsonDeserializer<T>) ArrayDeserializer.getInstance();
        }
        // For others, generic one works fine
        return (JsonDeserializer<T>) instance;
    }
    
    /*
    /**********************************************************
    /* Actual deserializer implementations
    /**********************************************************
     */

    @Override
    public JsonNode getNullValue(DeserializationContext ctxt) {
        return JsonNodeFactory.INSTANCE.nullNode();
    }

    /**
     * Implementation that will produce types of any JSON nodes; not just one
     * deserializer is registered to handle (in case of more specialized handler).
     * Overridden by typed sub-classes for more thorough checking
     */
    @Override
    public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        switch (p.currentTokenId()) {
            case JsonTokenId.ID_START_OBJECT:
                return deserializeObject(p, ctxt, JsonNodeFactory.INSTANCE);
            case JsonTokenId.ID_START_ARRAY:
                return deserializeArray(p, ctxt, JsonNodeFactory.INSTANCE);
            default:
        }
        return deserializeAny(p, ctxt, JsonNodeFactory.INSTANCE);
    }

    /*
    /**********************************************************
    /* Specific instances for more accurate types
    /**********************************************************
     */

    final static class ObjectDeserializer
            extends BaseNodeDeserializer<ObjectNode> {
        private static final long serialVersionUID = 1L;

        protected final static ObjectDeserializer _instance = new ObjectDeserializer();

        protected ObjectDeserializer() {
            super(ObjectNode.class, true);
        }

        public static ObjectDeserializer getInstance() {
            return _instance;
        }

        @Override
        public ObjectNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.isExpectedStartObjectToken()) {
                return deserializeObject(p, ctxt, JsonNodeFactory.INSTANCE);
            }
            if (p.hasToken(JsonToken.FIELD_NAME)) {
                return deserializeObjectAtName(p, ctxt, JsonNodeFactory.INSTANCE);
            }
            // 23-Sep-2015, tatu: Ugh. We may also be given END_OBJECT (similar to FIELD_NAME),
            //    if caller has advanced to the first token of Object, but for empty Object
            if (p.hasToken(JsonToken.END_OBJECT)) {
                return JsonNodeFactory.INSTANCE.objectNode();
            }
            return (ObjectNode) ctxt.handleUnexpectedToken(ObjectNode.class, p);
        }

        /**
         * Variant needed to support both root-level `updateValue()` and merging.
         *
         * @since 2.9
         */
        @Override
        public ObjectNode deserialize(JsonParser p, DeserializationContext ctxt,
                                      ObjectNode node) throws IOException {
            if (p.isExpectedStartObjectToken() || p.hasToken(JsonToken.FIELD_NAME)) {
                return (ObjectNode) updateObject(p, ctxt, (ObjectNode) node);
            }
            return (ObjectNode) ctxt.handleUnexpectedToken(ObjectNode.class, p);
        }
    }

    final static class ArrayDeserializer
            extends BaseNodeDeserializer<ArrayNode> {
        private static final long serialVersionUID = 1L;

        protected final static ArrayDeserializer _instance = new ArrayDeserializer();

        protected ArrayDeserializer() {
            super(ArrayNode.class, true);
        }

        public static ArrayDeserializer getInstance() {
            return _instance;
        }

        @Override
        public ArrayNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.isExpectedStartArrayToken()) {
                return deserializeArray(p, ctxt, JsonNodeFactory.INSTANCE);
            }
            return (ArrayNode) ctxt.handleUnexpectedToken(ArrayNode.class, p);
        }

        /**
         * Variant needed to support both root-level `updateValue()` and merging.
         *
         * @since 2.9
         */
        @Override
        public ArrayNode deserialize(JsonParser p, DeserializationContext ctxt,
                                     ArrayNode node) throws IOException {
            if (p.isExpectedStartArrayToken()) {
                return (ArrayNode) updateArray(p, ctxt, (ArrayNode) node);
            }
            return (ArrayNode) ctxt.handleUnexpectedToken(ArrayNode.class, p);
        }
    }
}

