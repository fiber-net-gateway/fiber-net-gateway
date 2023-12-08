package io.fiber.net.script.std;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

public class IncludesFunc implements Library.Function {

    @Override
    public void call(ExecutionContext context, JsonNode... args) {
        if (ArrayUtils.isEmpty(args)) {
            context.returnVal(this, BooleanNode.FALSE);
            return;
        }

        JsonNode c = args[0];
        boolean isText;
        if (!(isText = (c instanceof TextNode)) && !(c instanceof ArrayNode)) {
            context.returnVal(this, BooleanNode.FALSE);
            return;
        }

        int length = args.length;
        if (isText) {
            String t = c.textValue();
            JsonNode item;
            for (int i = 1; i < length; i++) {
                if (!(item = args[i]).isTextual() || !t.contains(item.textValue())) {
                    context.returnVal(this, BooleanNode.FALSE);
                    return;
                }
            }
        } else {
            for (int i = 1; i < length; i++) {
                if (!Compares.includes(c, args[i])) {
                    context.returnVal(this, BooleanNode.FALSE);
                    return;
                }
            }
        }
        context.returnVal(this, BooleanNode.TRUE);
    }
}
