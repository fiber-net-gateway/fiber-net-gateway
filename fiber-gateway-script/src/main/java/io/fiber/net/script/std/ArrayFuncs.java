package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.util.StringJoiner;

@ScriptLib(functionPrefix = "array")
public final class ArrayFuncs {
    private ArrayFuncs() {
    }

    @ScriptFunction(name = "join", params = {
            @ScriptParam("arr"),
            @ScriptParam(value = "delimiter", optional = true, defaultValue = "\"\"")
    })
    public static JsonNode join(JsonNode node, JsonNode delimiter) throws ScriptExecException {
        ArrayNode arr = assertArray(node, "array join");
        StringJoiner js = new StringJoiner(delimiter.asText(""));
        for (JsonNode arg : arr) {
            js.add(arg.asText(""));
        }
        return TextNode.valueOf(js.toString());
    }

    @ScriptFunction(name = "pop")
    public static JsonNode pop(@ScriptParam("arr") JsonNode node) throws ScriptExecException {
        ArrayNode arr = assertArray(node, "array pop");
        if (arr.isEmpty()) {
            return NullNode.getInstance();
        }
        return arr.remove(arr.size() - 1);
    }

    @ScriptFunction(name = "push")
    public static JsonNode push(@ScriptParam("arr") JsonNode node,
                                @ScriptParam(value = "items", variadic = true) JsonNode... items)
            throws ScriptExecException {
        ArrayNode arr = assertArray(node, "array pop");
        for (JsonNode item : items) {
            arr.add(item);
        }
        return arr;
    }

    private static ArrayNode assertArray(JsonNode node, String func) throws ScriptExecException {
        if (!node.isArray()) {
            throw new ScriptExecException(func + " require array but get " + node.getNodeType());
        }
        return (ArrayNode) node;
    }
}
