package io.fiber.net.common.codec;

import io.fiber.net.common.FiberException;

public class DataCodecException extends FiberException {
    public DataCodecException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public DataCodecException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public DataCodecException(String message) {
        this(message, 500, "ZIP_CODEC");
    }

    public DataCodecException(String message, Throwable cause) {
        this(message, cause, 500, "ZIP_CODEC");
    }
}
