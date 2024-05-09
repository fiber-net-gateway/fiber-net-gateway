package io.fiber.net.common.ext;


import io.fiber.net.common.Engine;

public interface StartListener<E, EH extends Engine<E>> {
    void onStart(EH engine) throws Exception;
}
