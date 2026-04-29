package io.fiber.net.script.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;

import java.lang.reflect.Method;

public interface DirectReflectInvoker {
    int CTX = 1;
    int HANDLE = 2;
    int ARGS = 3;
    int ARG = 4;
    int REST = 5;

    Method directMethod();

    Object directOwner();

    int[] directArgPlan();

    static JsonNode[] restArgs(Library.Arguments args, int off) {
        int count = args.getArgCnt() - off;
        if (count <= 0) {
            return new JsonNode[0];
        }
        JsonNode[] nodes = new JsonNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = args.getArgVal(off + i);
        }
        return nodes;
    }
}
