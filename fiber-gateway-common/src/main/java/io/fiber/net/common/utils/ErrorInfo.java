package io.fiber.net.common.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fiber.net.common.FiberException;

import java.util.Map;

public class ErrorInfo {

    public static ErrorInfo of(Throwable e) {
        int status = 500;
        String name;

        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.message = e.getMessage();

        if (e instanceof FiberException) {
            status = ((FiberException) e).getCode();
            name = ((FiberException) e).getErrorName();
        } else {
            name = "FIBER_UNKNOWN_ERROR-" + e.getClass().getName();
        }
        errorInfo.name = name;
        errorInfo.status = status;
        return errorInfo;
    }

    private String name;
    private String message;
    @JsonIgnore
    private int status;
    private Map<String, Object> meta;

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonIgnore
    public int getStatus() {
        return status;
    }

    @JsonIgnore
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ErrorInfo{" +
                "name='" + name + '\'' +
                ", message='" + message + '\'' +
                ", status=" + status +
                ", meta=" + meta +
                '}';
    }
}
