package io.fiber.net.script.run;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.script.Script;

public class FiberScript implements Script {


    public FiberScript(Injector injector) {
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return null;
    }





}
