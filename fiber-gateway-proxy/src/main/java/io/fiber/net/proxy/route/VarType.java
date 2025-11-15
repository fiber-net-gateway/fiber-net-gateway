package io.fiber.net.proxy.route;

public enum VarType {
    PATH,
    QUERY,
    REQ_HEADER,
    COOKIE,
    RESP_HEADER,
    CONTEXT;
    public static final VarType[] _VALUES = VarType.values();
    public static final int LENGTH = _VALUES.length;
}
