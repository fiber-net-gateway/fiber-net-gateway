package io.fiber.net.proxy;

import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public interface HttpLibConfigure {
    void onInit(ExtensiveHttpLib lib);

    Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals);
}
