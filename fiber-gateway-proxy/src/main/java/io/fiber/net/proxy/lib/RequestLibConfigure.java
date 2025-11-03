package io.fiber.net.proxy.lib;

import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.script.Library;

import java.util.Map;

public class RequestLibConfigure implements HttpLibConfigure {
    @Override
    public void onInit(ExtensiveHttpLib lib) {
        for (Map.Entry<String, Library.AsyncFunction> entry : ReqFunc.ASYNC_FC_MAP.entrySet()) {
            lib.putAsyncFunc(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Library.Function> entry : ReqFunc.FC_MAP.entrySet()) {
            lib.putFunc(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, SyncHttpFunc> entry : RespFunc.FC_MAP.entrySet()) {
            lib.putFunc(entry.getKey(), entry.getValue());
        }

    }
}
