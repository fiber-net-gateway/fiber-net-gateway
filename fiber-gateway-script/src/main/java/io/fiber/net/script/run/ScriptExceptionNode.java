package io.fiber.net.script.run;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import io.fiber.net.common.json.ExceptionNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ScriptExecException;

import java.io.IOException;

public class ScriptExceptionNode extends ExceptionNode {

    public static ScriptExceptionNode of(ScriptExecException e, long pos) {
        return new ScriptExceptionNode(e, pos);
    }

    public static ScriptExceptionNode of(ScriptExecException e) {
        ScriptExceptionNode node = new ScriptExceptionNode(e);
        if (e.getErrorNode() == null) {
            e.setErrorNode(node);
        }
        return node;
    }

    public static ScriptExceptionNode of(String msg, long pos) {
        ScriptExecException exception = new ScriptExecException(msg);
        return new ScriptExceptionNode(exception, pos);
    }

    public ScriptExceptionNode(ScriptExecException exception, long pos) {
        super(exception);
        exception.setPos(pos);
    }

    public ScriptExceptionNode(ScriptExecException exception) {
        super(exception);
    }

    @Override
    public ScriptExecException getException() {
        return (ScriptExecException) super.getException();
    }

    @Override
    public void serialize(JsonGenerator g, SerializerProvider provider) throws IOException {
        ScriptExecException exception = getException();
        g.writeStartObject(this);
        g.writeNumberField("code", exception.getCode());
        g.writeStringField("name", exception.getErrorName());
        g.writeStringField("message", exception.getMessage());
        if (exception.getPos() >= 0) {
            g.writeNumberField("beginPos", exception.getBeginPos());
            g.writeNumberField("endPos", exception.getEndPos());
        }
        JsonNode errorNode = exception.getErrorNode();
        if (errorNode != null && errorNode != this) {
            g.writeObjectField("meta", errorNode);
            g.writeTree(errorNode);
            return;
        }
        g.writeEndObject();
    }

    @Override
    public void serializeWithType(JsonGenerator g, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
        ScriptExecException exception = getException();
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(g,
                typeSer.typeId(this, JsonToken.START_OBJECT));
        g.writeNumberField("code", exception.getCode());
        g.writeStringField("name", exception.getErrorName());
        g.writeStringField("message", exception.getMessage());
        if (exception.getPos() >= 0) {
            g.writeNumberField("beginPos", exception.getBeginPos());
            g.writeNumberField("endPos", exception.getEndPos());
        }
        typeSer.writeTypeSuffix(g, typeIdDef);
    }
}
