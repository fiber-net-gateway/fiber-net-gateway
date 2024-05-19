package io.fiber.net.dubbo.nacos;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.modules.ObjectWriterModule;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.JsonUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;

public class JsonNodeForFastJson2 {
    private static class JsonNodeWriter implements ObjectWriter<JsonNode> {
        private static final JsonNodeWriter INSTANCE = new JsonNodeWriter();

        @Override
        public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
            JsonNode node = (JsonNode) o;
            switch (node.getNodeType()) {
                case ARRAY: {
                    int size = node.size();
                    jsonWriter.startArray(size);
                    for (int i = 0; i < size; i++) {
                        write(jsonWriter, node.get(i), null, null, l);
                    }
                    jsonWriter.endArray();
                    break;
                }
                case BINARY: {
                    try {
                        jsonWriter.writeBinary(node.binaryValue());
                    } catch (IOException ignore) {
                    }
                    break;
                }
                case BOOLEAN:
                    jsonWriter.writeBool(node.booleanValue());
                    break;
                case MISSING:
                case NULL:
                    jsonWriter.writeNull();
                    break;
                case NUMBER: {
                    if (node.isInt()) {
                        jsonWriter.writeInt32(node.intValue());
                    } else if (node.isLong()) {
                        jsonWriter.writeInt64(node.longValue());
                    } else if (node.isFloat()) {
                        jsonWriter.writeFloat(node.floatValue());
                    } else if (node.isDouble()) {
                        jsonWriter.writeDouble(node.doubleValue());
                    } else if (node.isShort()) {
                        jsonWriter.writeInt16(node.shortValue());
                    } else if (node.isBigDecimal()) {
                        jsonWriter.writeDecimal(node.decimalValue());
                    } else if (node.isBigInteger()) {
                        jsonWriter.writeBigInt(node.bigIntegerValue());
                    } else {
                        jsonWriter.writeString("<unknown json number type>");
                    }
                    break;
                }
                case OBJECT: {
                    jsonWriter.startObject();
                    if (!node.isEmpty()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                        do {
                            Map.Entry<String, JsonNode> next = fields.next();
                            jsonWriter.writeName(next.getKey());
                            if (!jsonWriter.jsonb) {
                                jsonWriter.writeColon();
                            }
                            write(jsonWriter, next.getValue(), null, null, l);
                        } while (fields.hasNext());
                    }
                    jsonWriter.endObject();
                    break;
                }

                case STRING:
                    jsonWriter.writeString(node.textValue());
                    break;
                case EXCEPTION:
                case ITERATOR: {
                    write(jsonWriter, JsonUtil.valueToTree(node), null, null, l);
                    break;
                }
            }
        }
    }

    private static class JsonNodeWriterModule implements ObjectWriterModule {
        private static final JsonNodeWriterModule INSTANCE = new JsonNodeWriterModule();

        @SuppressWarnings("rawtypes")
        @Override
        public ObjectWriter getObjectWriter(Type objectType, Class objectClass) {
            if (JsonNode.class.isAssignableFrom(objectClass)) {
                return JsonNodeWriter.INSTANCE;
            }
            return null;
        }
    }

    public static void config(ObjectWriterProvider provider) {
        provider.register(JsonNodeWriterModule.INSTANCE);

        provider.register(ArrayNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(BaseJsonNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(BigIntegerNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(BinaryNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(BooleanNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(ContainerNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(DecimalNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(DoubleNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(FloatNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(IntNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(JsonNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(LongNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(MissingNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(NullNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(NumericNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(ObjectNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(ShortNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(TextNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(ValueNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(ExceptionNode.class, JsonNodeWriter.INSTANCE, true);
        provider.register(IteratorNode.class, JsonNodeWriter.INSTANCE, true);

        provider.register(ArrayNode.class, JsonNodeWriter.INSTANCE);
        provider.register(BaseJsonNode.class, JsonNodeWriter.INSTANCE);
        provider.register(BigIntegerNode.class, JsonNodeWriter.INSTANCE);
        provider.register(BinaryNode.class, JsonNodeWriter.INSTANCE);
        provider.register(BooleanNode.class, JsonNodeWriter.INSTANCE);
        provider.register(ContainerNode.class, JsonNodeWriter.INSTANCE);
        provider.register(DecimalNode.class, JsonNodeWriter.INSTANCE);
        provider.register(DoubleNode.class, JsonNodeWriter.INSTANCE);
        provider.register(FloatNode.class, JsonNodeWriter.INSTANCE);
        provider.register(IntNode.class, JsonNodeWriter.INSTANCE);
        provider.register(JsonNode.class, JsonNodeWriter.INSTANCE);
        provider.register(LongNode.class, JsonNodeWriter.INSTANCE);
        provider.register(MissingNode.class, JsonNodeWriter.INSTANCE);
        provider.register(NullNode.class, JsonNodeWriter.INSTANCE);
        provider.register(NumericNode.class, JsonNodeWriter.INSTANCE);
        provider.register(ObjectNode.class, JsonNodeWriter.INSTANCE);
        provider.register(ShortNode.class, JsonNodeWriter.INSTANCE);
        provider.register(TextNode.class, JsonNodeWriter.INSTANCE);
        provider.register(ValueNode.class, JsonNodeWriter.INSTANCE);
        provider.register(ExceptionNode.class, JsonNodeWriter.INSTANCE);
        provider.register(IteratorNode.class, JsonNodeWriter.INSTANCE);
    }

}
