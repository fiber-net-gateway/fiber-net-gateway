package io.fiber.net.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import io.fiber.net.common.FiberException;

import java.io.IOException;

public class ExceptionNode extends BaseJsonNode {
    protected final FiberException exception;

    public ExceptionNode(FiberException exception) {
        this.exception = exception;
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

    public FiberException getException() {
        return exception;
    }

    @Override
    public JsonNode get(String fieldName) {
        if ("name".equals(fieldName)) {
            return TextNode.valueOf(exception.getErrorName());
        } else if ("message".equals(fieldName)) {
            return TextNode.valueOf(exception.getMessage());
        } else if ("code".equals(fieldName)) {
            return IntNode.valueOf(exception.getCode());
        }
        return null;
    }

    @Override
    public JsonNode path(String fieldName) {
        JsonNode node = get(fieldName);
        if (node != null) {
            return node;
        }
        return MissingNode.getInstance();
    }

    @Override
    public JsonNode path(int index) {
        return MissingNode.getInstance();
    }

    @Override
    protected JsonNode _at(JsonPointer ptr) {
        return MissingNode.getInstance();
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.EXCEPTION;
    }

    @Override
    public String asText() {
        return "<exception>";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ExceptionNode) {
            return exception.equals(((ExceptionNode) o).exception);
        }
        return false;
    }


    @Override
    public int hashCode() {
        return exception.hashCode();
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.START_OBJECT;
    }

    @Override
    public void serialize(JsonGenerator g, SerializerProvider provider) throws IOException {
        g.writeStartObject(this);
        g.writeNumberField("code", exception.getCode());
        g.writeStringField("name", exception.getErrorName());
        g.writeStringField("message", exception.getMessage());
        g.writeEndObject();
    }

    @Override
    public void serializeWithType(JsonGenerator g, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(g,
                typeSer.typeId(this, JsonToken.START_OBJECT));
        g.writeNumberField("code", exception.getCode());
        g.writeStringField("name", exception.getErrorName());
        g.writeStringField("message", exception.getMessage());
        typeSer.writeTypeSuffix(g, typeIdDef);
    }
}
