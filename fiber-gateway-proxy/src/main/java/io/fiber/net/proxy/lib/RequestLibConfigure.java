package io.fiber.net.proxy.lib;

import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.script.lib.ReflectLib;

public class RequestLibConfigure implements HttpLibConfigure {
    @Override
    public void onInit(ExtensiveHttpLib lib) {
        ReflectLib.registerStatic(lib, ReqFunc.class);
        ReflectLib.registerStatic(lib, RespFunc.class);
    }
}
