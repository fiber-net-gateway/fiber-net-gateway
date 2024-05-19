package io.fiber.net.common;

public class FiberException extends Exception {
    private int code;
    private final String errorName;

    public FiberException(String message, int code, String errorName) {
        super(message);
        this.code = code;
        this.errorName = errorName;
    }

    public FiberException(String message, Throwable cause, int code, String errorName) {
        super(message, cause);
        this.code = code;
        this.errorName = errorName;
    }

    public FiberException(Throwable cause, int code, String errorName) {
        super(cause);
        this.code = code;
        this.errorName = errorName;
    }

    public int getCode() {
        return code;
    }

    public String getErrorName() {
        return errorName;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getMeta() {
        return null;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
