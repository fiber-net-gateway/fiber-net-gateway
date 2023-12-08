package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ObjectsFuncs {

    private static ObjectNode firstObj(JsonNode[] args) throws ScriptExecException {
        if (ArrayUtils.isEmpty(args)) {
            throw new ScriptExecException("require object");
        }
        if (!args[0].isObject()) {
            throw new ScriptExecException("require object but get " + args[0].getNodeType());
        }

        return (ObjectNode) args[0];
    }

    static class AssignMethod implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            ObjectNode t;
            try {
                t = firstObj(args);
            } catch (ScriptExecException e) {

                context.throwErr(this, e);
                return;
            }
            int length = args.length;
            if (length < 2) {
                context.throwErr(this, new ScriptExecException("assignObject empty params"));
                return;
            }
            for (int i = 1; i < args.length; i++) {
                JsonNode arg = args[i];
                if (arg instanceof ObjectNode) {
                    t.setAll((ObjectNode) arg);
                }
            }

            context.returnVal(this, t);
            return;
        }
    }

    static class KeysMethod implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            ObjectNode t;
            try {
                t = firstObj(args);
            } catch (ScriptExecException e) {
                context.throwErr(this, e);
                return;
            }
            ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
            for (Iterator<String> ks = t.fieldNames(); ks.hasNext(); ) {
                arrayNode.add(ks.next());
            }
            context.returnVal(this, arrayNode);
        }
    }

    static class ValuesMethod implements Library.Function {


        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            ObjectNode t;
            try {
                t = firstObj(args);
            } catch (ScriptExecException e) {
                context.throwErr(this, e);
                return;
            }
            ArrayNode arrayNode = JsonUtil.createArrayNode(t.size());
            for (JsonNode jsonNode : t) {
                arrayNode.add(jsonNode);
            }
            context.returnVal(this, arrayNode);
        }
    }

    static final Map<String, Library.Function> FUNC = new HashMap<>();

    static class DeleteKeyFunc implements Library.Function {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            int length = args.length;
            if (length < 2) {
                context.throwErr(this, new ScriptExecException("deleteObjectKey params undefined"));
                return;
            }

            JsonNode arg = args[0];
            if (!arg.isObject()) {
                context.throwErr(this, new ScriptExecException("deleteObjectKey not support " + arg.getNodeType()));
                return;
            }

            ObjectNode obj = (ObjectNode) arg;
            for (int i = 1; i < length; i++) {
                if (args[i].isTextual()) {
                    obj.remove(args[i].textValue());
                }
            }

            context.returnVal(this, obj);
        }
    }

    static {
        FUNC.put("Object.assign", new AssignMethod());
        FUNC.put("Object.keys", new KeysMethod());
        FUNC.put("Object.values", new ValuesMethod());
        FUNC.put("Object.delete", new DeleteKeyFunc());
    }
}
