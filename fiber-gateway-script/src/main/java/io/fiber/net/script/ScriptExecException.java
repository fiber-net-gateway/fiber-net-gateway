package io.fiber.net.script;


import io.fiber.net.common.FiberException;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.ast.AstUtils;

public class ScriptExecException extends FiberException {
    public static final String ERROR_NAME = "JSON_EXPRESSION_EVALUATION";
    private long pos = -1;
    private JsonNode errorNode;

    public ScriptExecException(String message) {
        super(message, 500, ERROR_NAME);
    }

    public ScriptExecException(String message, int code) {
        super(message, code, ERROR_NAME);
    }

    public ScriptExecException(String message, int code, String errorName) {
        super(message, code, errorName);
    }

    public ScriptExecException(String message, Throwable cause, int code, String errorName) {
        super(message, cause, code, errorName);
    }

    public ScriptExecException(Throwable cause, int code, String errorName) {
        super(cause, code, errorName);
    }

    public ScriptExecException(String message, Throwable cause) {
        super(message, cause, 500, ERROR_NAME);
    }

    public static ScriptExecException fromThrowable(Throwable cause) {
        if (cause instanceof ScriptExecException) {
            return (ScriptExecException) cause;
        }

        if (cause instanceof FiberException) {
            FiberException fe = (FiberException) cause;
            return new ScriptExecException(fe.getMessage(), cause, fe.getCode(),
                    fe.getErrorName());
        }

        return new ScriptExecException(cause.getMessage(), cause);
    }

    public long getPos() {
        return pos;
    }

    public void setPos(long pos) {
        this.pos = pos;
    }

    public int getBeginPos() {
        return AstUtils.startPos(pos);
    }

    public int getEndPos() {
        return AstUtils.endPos(pos);
    }

    public JsonNode getErrorNode() {
        return errorNode;
    }

    public void setErrorNode(JsonNode errorNode) {
        this.errorNode = errorNode;
    }
}
