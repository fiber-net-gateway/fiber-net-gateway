package io.fiber.net.common.json;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;

public abstract class IteratorNode extends BaseJsonNode {

    @Override
    public JsonToken asToken() {
        return JsonToken.START_ARRAY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends JsonNode> T deepCopy() {
        return (T) this;
    }

    @Override
    public JsonNode get(int index) {
        return null;
    }

    @Override
    public JsonNode path(String fieldName) {
        return MissingNode.getInstance();
    }

    @Override
    public JsonNode path(int index) {
        return MissingNode.getInstance();
    }

    @Override
    protected JsonNode _at(JsonPointer ptr) {
        return null;
    }

    @Override
    public final JsonNodeType getNodeType() {
        return JsonNodeType.ITERATOR;
    }

    @Override
    public String asText() {
        return "<iterator>";
    }

    public abstract boolean next();

    public abstract JsonNode currentKey();

    public abstract JsonNode currentValue();
}
