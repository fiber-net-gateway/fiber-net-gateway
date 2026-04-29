package io.fiber.net.script;

import io.fiber.net.common.json.JsonNode;

public interface ExecutionContext {

    JsonNode root();

    Object attach();

}
