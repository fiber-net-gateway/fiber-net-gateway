package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ObjectNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ObjectsFuncs {

    private static ObjectNode firstObj(ExecutionContext context) throws ScriptExecException {
        if (context.noArgs()) {
            throw new ScriptExecException("require object");
        }
        JsonNode argVal = context.getArgVal(0);
        if (!argVal.isObject()) {
            throw new ScriptExecException("require object but get " + argVal.getNodeType());
        }

        return (ObjectNode) argVal;
    }

    static class AssignMethod implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            ObjectNode t = firstObj(context);
            int length = context.getArgCnt();
            if (length < 2) {
                throw new ScriptExecException("assignObject empty params");
            }
            for (int i = 1; i < length; i++) {
                JsonNode arg = context.getArgVal(i);
                if (arg instanceof ObjectNode) {
                    t.setAll((ObjectNode) arg);
                }
            }

            return t;
        }
    }

    static class KeysMethod implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            ObjectNode t = firstObj(context);
            ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
            for (Iterator<String> ks = t.fieldNames(); ks.hasNext(); ) {
                arrayNode.add(ks.next());
            }
            return arrayNode;
        }
    }

    static class ValuesMethod implements Library.Function {


        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            ObjectNode t = firstObj(context);
            ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
            for (JsonNode jsonNode : t) {
                arrayNode.add(jsonNode);
            }
            return arrayNode;
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static class DeleteKeyFunc implements Library.Function {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            int length = context.getArgCnt();
            if (length < 2) {
                throw new ScriptExecException("assign ObjectKey params undefined");
            }

            JsonNode arg = context.getArgVal(0);
            if (!arg.isObject()) {
                throw new ScriptExecException("assign ObjectKey not support " + arg.getNodeType());
            }

            ObjectNode obj = (ObjectNode) arg;
            int size = 0;
            for (int i = 1; i < length; i++) {
                if (context.getArgVal(i).isTextual()) {
                    if (obj.remove(context.getArgVal(i).textValue()) != null) {
                        size++;
                    }
                }
            }

            return IntNode.valueOf(size);
        }
    }

    static {
        FUNC.put("Object.assign", new AssignMethod());
        FUNC.put("Object.keys", new KeysMethod());
        FUNC.put("Object.values", new ValuesMethod());
        FUNC.put("Object.delete", new DeleteKeyFunc());
    }
}
