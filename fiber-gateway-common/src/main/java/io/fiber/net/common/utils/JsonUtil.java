package io.fiber.net.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class JsonUtil {

    public final static ObjectMapper MAPPER;

    static {
        if (SystemPropertyUtil.getBoolean("soa.json.intern", false))
            MAPPER = new ObjectMapper();
        else
            MAPPER = new ObjectMapper(new MappingJsonFactory().disable(JsonFactory.Feature.INTERN_FIELD_NAMES));

        if (SystemPropertyUtil.getBoolean("soa.json.use_jsr310", false)) {
            // MAPPER.registerModule(new JavaTimeModule());
            // 直接使用 JavaTimeModule 会在JsonUtil initialize前校验会提前加载 JavaTimeModule。导致报错
            // 这样第一次使用 $TryLoadJsr310的时候才会加载JavaTimeModule
            // 没有initialize就不会加载它的reference。防止用户没有引入 jackson jsr310 包
            $TryLoadJsr310.registryTimeModule();
        } else {
            MAPPER.registerModule(initModule());
        }
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)//
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)//
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        switch (SystemPropertyUtil.get("soa.jsonignore.level", "none")) {
            case "null":
                MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                break;
            case "empty":
                MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
                break;
        }

    }

    private static class $TryLoadJsr310 {
        static void registryTimeModule() {
            MAPPER.registerModule(new JavaTimeModule());
        }
    }

    private static SimpleModule initModule() {
        return new SimpleModule().//
                addSerializer(BigDecimal.class, new BigDecimalSerializer()).//
                addSerializer(LocalTime.class, new LocalTimeSerializer()).//
                addDeserializer(LocalTime.class, new LocalTimeDeserializer()).//
                addSerializer(LocalDate.class, new LocalDateSerializer()).//
                addDeserializer(LocalDate.class, new LocalDateDeserializer()).//
                addSerializer(LocalDateTime.class, new LocalDateTimeSerializer()).//
                addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
    }

    public static JavaType genJavaType(Type type) {
        return MAPPER.getTypeFactory().constructType(type);
    }

    public static JavaType genJavaType(TypeReference<?> type) {
        return MAPPER.getTypeFactory().constructType(type);
    }

    public static TextNode createTextNode(String text) {
        return MAPPER.getNodeFactory().textNode(text);
    }

    public static NumericNode numberNode(long val) {
        return MAPPER.getNodeFactory().numberNode(val);
    }

    public static NumericNode numberNode(int val) {
        return MAPPER.getNodeFactory().numberNode(val);
    }

    public static NumericNode numberNode(double val) {
        return MAPPER.getNodeFactory().numberNode(val);
    }

    public static BooleanNode booleanNode(boolean val) {
        return MAPPER.getNodeFactory().booleanNode(val);
    }

    public static ArrayNode createArrayNode(int capacity) {
        return MAPPER.getNodeFactory().arrayNode();
    }

    public static ArrayNode createArrayNode() {
        return MAPPER.getNodeFactory().arrayNode();
    }

    public static ObjectNode createObjectNode() {
        return MAPPER.getNodeFactory().objectNode();
    }

    private static class BigDecimalSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(value.toString());
        }
    }

    private static class LocalTimeSerializer extends JsonSerializer<LocalTime> {
        @Override
        public void serialize(LocalTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(DateTimeFormatter.ISO_LOCAL_TIME.format(value));
        }
    }

    private static class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {
        @Override
        public LocalTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String dateString = ((JsonNode) jp.getCodec().readTree(jp)).asText();
            return LocalTime.parse(dateString, DateTimeFormatter.ISO_LOCAL_TIME);
        }
    }

    private static class LocalDateSerializer extends JsonSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(DateTimeFormatter.ISO_LOCAL_DATE.format(value));
        }
    }

    private static class LocalDateDeserializer extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String dateString = ((JsonNode) jp.getCodec().readTree(jp)).asText();
            return LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value));
        }
    }

    private static class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String dateTimeStr = ((JsonNode) jp.getCodec().readTree(jp)).asText();
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static boolean isNullOrEmpty(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                || node.isTextual() && StringUtils.isEmpty(node.textValue());
    }

    public static boolean isNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    public static boolean isNull(Object node) {
        return node == null || node instanceof NullNode || node instanceof MissingNode;
    }

    public static JsonNode shallowCopy(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode objectNode = MAPPER.createObjectNode();
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
