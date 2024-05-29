package io.fiber.net.proxy.gov;

import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;

import java.util.List;

public class GovLibConfigure implements HttpLibConfigure {
    @Override
    public void onInit(ExtensiveHttpLib lib) {
    }

    @Override
    public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        if ("rate_limiter".equals(type)) {
            return RateLimiterFunc.getRateLimiterFunc(name, literals);
        }
        return null;
    }

}
