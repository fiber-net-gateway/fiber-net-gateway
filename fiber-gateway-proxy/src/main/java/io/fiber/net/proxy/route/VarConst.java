package io.fiber.net.proxy.route;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.server.HttpExchange;

public class VarConst implements SyncHttpConst {
    private final VarType type;
    private final String nameTxt;
    private int idx;
    final byte[] name;
    final int hash;

    public VarConst(VarType type, String nameTxt, int idx, byte[] name, int hash) {
        this.type = type;
        this.nameTxt = nameTxt;
        this.idx = idx;
        this.name = name;
        this.hash = hash;
    }

    @Override
    public JsonNode get(ExecutionContext executionContext) throws ScriptExecException {
        return AbstractRouteContext.getVar((HttpExchange) executionContext.attach(), type, idx);
    }

    public int getIdx() {
        return idx;
    }

    public void setIdx(int idx) {
        this.idx = idx;
    }

    public VarType getType() {
        return type;
    }

    public byte[] getName() {
        return name;
    }

    public int getHash() {
        return hash;
    }

    public String getNameTxt() {
        return nameTxt;
    }
}
