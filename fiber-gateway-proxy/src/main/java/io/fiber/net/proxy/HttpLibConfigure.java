package io.fiber.net.proxy;

import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public interface HttpLibConfigure {
    void onInit(ExtensiveHttpLib lib);

    default Library.Constant findConstant(String namespace, String key) {
        return null;
    }

    default Library.AsyncConstant findAsyncConstant(String namespace, String key) {
        return null;
    }

    default Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        return null;
    }
}
