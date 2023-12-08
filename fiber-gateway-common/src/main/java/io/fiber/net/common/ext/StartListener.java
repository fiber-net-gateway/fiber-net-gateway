package io.fiber.net.common.ext;

import io.fiber.net.common.Engine;

public interface StartListener {
    void onStart(Engine engine) throws Exception;
}
