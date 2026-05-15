package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;

public final class FunctionParam {
    private final String name;
    private final ValueNode defaultValue;
    private final boolean hasDefault;
    private final boolean variadic;

    private FunctionParam(String name, ValueNode defaultValue, boolean hasDefault, boolean variadic) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.hasDefault = hasDefault;
        this.variadic = variadic;
        if (hasDefault && !isSupportedDefaultValue(defaultValue)) {
            throw new IllegalArgumentException("default value must be number, boolean, null or string literal");
        }
    }

    public static FunctionParam required(String name) {
        return new FunctionParam(name, null, false, false);
    }

    public static FunctionParam optional(String name, ValueNode defaultValue) {
        return new FunctionParam(name, defaultValue, true, false);
    }

    public static FunctionParam variadic(String name) {
        return new FunctionParam(name, null, false, true);
    }

    private static boolean isSupportedDefaultValue(JsonNode node) {
        return node != null && (node.isNumber() || node.isBoolean() || node.isNull() || node.isTextual());
    }

    public String getName() {
        return name;
    }

    public ValueNode getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    public boolean isVariadic() {
        return variadic;
    }
}
