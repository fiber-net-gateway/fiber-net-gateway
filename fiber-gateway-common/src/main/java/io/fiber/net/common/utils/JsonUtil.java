package io.fiber.net.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;

public class JsonUtil {
    public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.INSTANCE;
    public final static ObjectMapper MAPPER;

    static {
        if (SystemPropertyUtil.getBoolean("fiber.json.intern", false))
            MAPPER = new ObjectMapper();
        else
            MAPPER = new ObjectMapper(new MappingJsonFactory().disable(JsonFactory.Feature.INTERN_FIELD_NAMES));

        if (SystemPropertyUtil.getBoolean("fiber.json.use_jsr310", false)) {
            // MAPPER.registerModule(new JavaTimeModule());
            // 直接使用 JavaTimeModule 会在JsonUtil initialize前校验会提前加载 JavaTimeModule。导致报错
            // 这样第一次使用 $TryLoadJsr310的时候才会加载JavaTimeModule
            // 没有initialize就不会加载它的reference。防止用户没有引入 jackson jsr310 包
            $TryLoadJsr310.registryTimeModule();
        }
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)//
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);


        SimpleModule module = new SimpleModule();
        addJsonNodeModule(module, JsonNode.class);
        addJsonNodeModule(module, ObjectNode.class);
        addJsonNodeModule(module, ArrayNode.class);
        addJsonNodeModule(module, NullNode.class);
        addJsonNodeModule(module, NumericNode.class);
        addJsonNodeModule(module, MissingNode.class);
        addJsonNodeModule(module, BooleanNode.class);
        addJsonNodeModule(module, BinaryNode.class);
        addJsonNodeModule(module, BigIntegerNode.class);
        addJsonNodeModule(module, DecimalNode.class);
        addJsonNodeModule(module, LongNode.class);
        addJsonNodeModule(module, IntNode.class);
        addJsonNodeModule(module, ShortNode.class);
        addJsonNodeModule(module, TextNode.class);
        addJsonNodeModule(module, ContainerNode.class);
        addJsonNodeModule(module, FloatNode.class);
        addJsonNodeModule(module, DecimalNode.class);
        addJsonNodeModule(module, BaseJsonNode.class);
        MAPPER.registerModule(module);

        switch (SystemPropertyUtil.get("fiber.jsonignore.level", "none")) {
            case "null":
                MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                break;
            case "empty":
                MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
                break;
        }

    }

    private static <T extends JsonNode> SimpleModule addJsonNodeModule(SimpleModule module, Class<T> cls) {
        module.addDeserializer(cls, JsonNodeDeserializer.getDeserializer(cls));
        return module;
    }

    private static class $TryLoadJsr310 {
        static void registryTimeModule() {
            MAPPER.registerModule(new JavaTimeModule());
        }
    }

    public static JavaType genJavaType(Type type) {
        return MAPPER.getTypeFactory().constructType(type);
    }

    public static JavaType genJavaType(TypeReference<?> type) {
        return MAPPER.getTypeFactory().constructType(type);
    }

    public static TextNode createTextNode(String text) {
        return TextNode.valueOf(text);
    }

    public static NumericNode numberNode(long val) {
        return LongNode.valueOf(val);
    }

    public static NumericNode numberNode(int val) {
        return IntNode.valueOf(val);
    }

    public static NumericNode numberNode(double val) {
        return DoubleNode.valueOf(val);
    }

    public static BooleanNode booleanNode(boolean val) {
        return BooleanNode.valueOf(val);
    }

    public static ArrayNode createArrayNode(int capacity) {
        return NODE_FACTORY.arrayNode(capacity);
    }

    public static ArrayNode createArrayNode() {
        return NODE_FACTORY.arrayNode();
    }

    public static ObjectNode createObjectNode() {
        return NODE_FACTORY.objectNode();
    }

    public static boolean isNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    public static boolean isNull(Object node) {
        return node == null || node instanceof NullNode || node instanceof MissingNode;
    }

    public static JsonNode shallowCopy(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode objectNode = NODE_FACTORY.objectNode();
            objectNode.setAll((ObjectNode) node);
            return objectNode;
        }
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = createArrayNode(node.size());
            arrayNode.addAll((ArrayNode) node);
            return arrayNode;
        }
        return node;
    }

    public static JsonNode readTree(String text) throws JsonProcessingException {
        return MAPPER.readValue(text, JsonNode.class);
    }

    public static JsonNode valueToTree(Object object) {
        return MAPPER.convertValue(object, JsonNode.class);
    }

    public static <T> T treeToValue(JsonNode node, Class<T> clz) throws IOException {
        return MAPPER.readValue(new TreeTraversingParser(node, MAPPER), clz);
    }

    public static <T> T treeToValue(JsonNode node, ResolvedType valueType) throws IOException {
        return MAPPER.readValue(new TreeTraversingParser(node, MAPPER), valueType);
    }

    public static JsonNode readTree(InputStream json) throws IOException {
        return MAPPER.readValue(json, JsonNode.class);
    }

    public static JsonNode readTree(JsonParser parser) throws IOException {
        return MAPPER.readValue(parser, JsonNode.class);
    }

    public static JsonNode readTree(byte[] bytes) throws IOException {
        return MAPPER.readValue(bytes, JsonNode.class);
    }

    public static JsonNode readTree(File json) throws IOException {
        return MAPPER.readValue(json, JsonNode.class);
    }

    public static JsonNode readTree(URL json) throws IOException {
        return MAPPER.readValue(json, JsonNode.class);
    }

    //  not null
    public static String toString(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "<nil>";
        }
        if (node.isArray()) {
            return "<ArrayNode>";
        }
        if (node.isObject()) {
            return "<ObjectNode>";
        }
        if (node.isBinary()) {
            return new String(((BinaryNode) node).binaryValue());
        }
        return node.asText();
    }

}
