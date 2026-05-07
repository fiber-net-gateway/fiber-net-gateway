package io.fiber.net.script.aot;

public class CfgException extends RuntimeException {
    public CfgException(String message) {
        super(message);
    }

    public CfgException(String message, Throwable cause) {
        super(message, cause);
    }
}
