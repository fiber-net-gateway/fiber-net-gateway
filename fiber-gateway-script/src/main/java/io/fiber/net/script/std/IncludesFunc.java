package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.run.Compares;

public class IncludesFunc implements Library.Function {

    @Override
    public JsonNode call(ExecutionContext context) {
        if (context.noArgs()) {
            return BooleanNode.FALSE;
        }

        JsonNode c = context.getArgVal(0);
        boolean isText;
        if (!(isText = (c instanceof TextNode)) && !(c instanceof ArrayNode)) {
            return BooleanNode.FALSE;
        }

        int length = context.getArgCnt();
        if (isText) {
            String t = c.textValue();
            JsonNode item;
            for (int i = 1; i < length; i++) {
                if (!(item = context.getArgVal(i)).isTextual() || !t.contains(item.textValue())) {
                    return BooleanNode.FALSE;
                }
            }
        } else {
            for (int i = 1; i < length; i++) {
                if (!Compares.includes(c, context.getArgVal(i))) {
                    return BooleanNode.FALSE;
                }
            }
        }
        return BooleanNode.TRUE;
    }
}
