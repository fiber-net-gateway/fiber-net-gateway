package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ObjectNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.util.Iterator;

@ScriptLib(functionPrefix = "Object")
public final class ObjectsFuncs {
    private ObjectsFuncs() {
    }

    @ScriptFunction(name = "assign")
    public static JsonNode assign(@ScriptParam("target") JsonNode target,
                                  @ScriptParam("source") JsonNode source,
                                  @ScriptParam(value = "sources", variadic = true) JsonNode... sources)
            throws ScriptExecException {
        ObjectNode t = firstObj(target);
        copyObject(t, source);
        for (JsonNode node : sources) {
            copyObject(t, node);
        }
        return t;
    }

    @ScriptFunction(name = "keys")
    public static JsonNode keys(@ScriptParam("obj") JsonNode obj) throws ScriptExecException {
        ObjectNode t = firstObj(obj);
        ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
        for (Iterator<String> ks = t.fieldNames(); ks.hasNext(); ) {
            arrayNode.add(ks.next());
        }
        return arrayNode;
    }

    @ScriptFunction(name = "values")
    public static JsonNode values(@ScriptParam("obj") JsonNode obj) throws ScriptExecException {
        ObjectNode t = firstObj(obj);
        ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
        for (JsonNode jsonNode : t) {
            arrayNode.add(jsonNode);
        }
        return arrayNode;
    }

    @ScriptFunction(name = "deleteProperties")
    public static JsonNode deleteProperties(@ScriptParam("obj") JsonNode obj,
                                            @ScriptParam("key") JsonNode key,
                                            @ScriptParam(value = "keys", variadic = true) JsonNode... keys)
            throws ScriptExecException {
        ObjectNode target = firstObj(obj);
        remove(target, key);
        for (JsonNode node : keys) {
            remove(target, node);
        }
        return obj;
    }

    private static ObjectNode firstObj(JsonNode argVal) throws ScriptExecException {
        if (!argVal.isObject()) {
            throw new ScriptExecException("require object but get " + argVal.getNodeType());
        }
        return (ObjectNode) argVal;
    }

    private static void copyObject(ObjectNode target, JsonNode source) {
        if (source instanceof ObjectNode) {
            target.setAll((ObjectNode) source);
        }
    }

    private static void remove(ObjectNode target, JsonNode key) {
        if (key.isTextual()) {
            target.remove(key.textValue());
        }
    }
}
