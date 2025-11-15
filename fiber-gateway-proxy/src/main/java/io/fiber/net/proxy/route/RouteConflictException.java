package io.fiber.net.proxy.route;

public class RouteConflictException extends RuntimeException {
    public RouteConflictException() {
    }

    public RouteConflictException(String message) {
        super(message);
    }

    public RouteConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
