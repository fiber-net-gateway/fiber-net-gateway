package io.fiber.net.script.run;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.internal.MaybeSubject;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

public abstract class AbstractVm implements ExecutionContext {

    public static final int STAT_INIT = 0;

    public static final int STAT_RUNNING = 1;

    public static final int STAT_INVOKING = 2;
    public static final int STAT_ASYNC = 3;

    public static final int STAT_RETURN = 4;
    public static final int STAT_THROW = 5;

    public static final int STAT_END_SEC = 7;
    public static final int STAT_END_ERR = 8;
    public static final int STAT_ABORT = 9;


    protected static class ResultSubject extends MaybeSubject<JsonNode> {
        @Override
        protected void onDismissClear(JsonNode value) {
        }
    }

    private final ResultSubject result = new ResultSubject();

    protected final JsonNode root;
    protected final Object attach;

    protected int state;
    protected ScriptExecException rtError;
    protected JsonNode rtValue;

    protected AbstractVm(JsonNode root, Object attach) {
        this.root = root;
        this.attach = attach;
    }

    public final Maybe<JsonNode> exec() {
        iterate();
        return result;
    }

    public final boolean isEnd() {
        int state = this.state;
        return state == STAT_END_SEC || state == STAT_END_ERR;
    }

    protected final boolean callAsyncFunc(Library.AsyncFunction function) {
        if (state != STAT_RUNNING) {
            throw new IllegalStateException("vm not running??");
        }
        state = STAT_INVOKING;
        function.call(this);
        if (state != STAT_INVOKING) {
            return false;
        }
        state = STAT_ASYNC;
        rtValue = null;
        rtError = null;
        return true;
    }

    protected final boolean callAsyncConst(Library.AsyncConstant cn) {
        if (state != STAT_RUNNING) {
            throw new IllegalStateException("vm not running??");
        }
        state = STAT_INVOKING;
        cn.get(this);
        if (state != STAT_INVOKING) {
            return false;
        }
        state = STAT_ASYNC;
        rtValue = null;
        rtError = null;
        return true;
    }

    @Override
    public final void returnVal(JsonNode value) {
        int s = state;
        if (s != STAT_INVOKING && s != STAT_ASYNC) {
            throw new IllegalStateException("vm not in resume");
        }
        state = STAT_RETURN;
        rtError = null;
        rtValue = value == null ? MissingNode.getInstance() : value;
        if (s == STAT_ASYNC) {
            resumeForIterate();
            iterate();
        }
    }

    @Override
    public final void throwErr(ScriptExecException node) {
        int s = state;
        if (s != STAT_INVOKING && s != STAT_ASYNC) {
            throw new IllegalStateException("vm not in resume");
        }

        state = STAT_THROW;
        rtError = node;
        rtValue = null;

        if (s == STAT_ASYNC) {
            resumeForIterate();
            iterate();
        }
    }


    protected final void iterate() {
        int state = this.state;
        assert state != STAT_ASYNC;
        if (state == STAT_INIT) {
            this.state = state = STAT_RUNNING;
        }

        if (state == STAT_RUNNING) {
            try {
                run();
            } catch (ScriptExecException e) {
                this.state = STAT_END_ERR;
                rtError = e;
            } catch (Throwable e) {
                this.state = STAT_ABORT;
                result.onError(e);
                return;
            }
        }
        if (this.state == STAT_END_SEC) {
            JsonNode v = rtValue;
            if (v != null) {
                result.onSuccess(v);
            } else {
                result.onComplete();
            }
        } else if (this.state == STAT_END_ERR) {
            assert rtError != null;
            result.onError(rtError);
        }
    }

    protected final JsonNode errorToObj() {
        ScriptExecException e = rtError;
        assert e != null;
        rtError = null;
        JsonNode errorNode = e.getErrorNode();
        if (errorNode != null) {
            return errorNode;
        }
        return ScriptExceptionNode.of(e);
    }

    protected final ScriptExecException objToError(JsonNode node) {
        if (node instanceof ScriptExceptionNode) {
            return ((ScriptExceptionNode) node).getException();
        }
        String name = "EXEC_UNKNOWN_ERROR", msg = "execute scripe error";
        int code = 500;
        JsonNode v;
        if ((v = node.get("name")) != null) {
            name = v.asText(name);
        }
        if ((v = node.get("message")) != null) {
            msg = v.asText(msg);
        }
        if ((v = node.get("status")) != null) {
            code = v.asInt(code);
        }
        ScriptExecException exception = new ScriptExecException(name, code, msg);
        exception.setErrorNode(node);
        return exception;
    }

    protected void resumeForIterate() {
        state = STAT_RUNNING;
    }

    protected abstract void run() throws ScriptExecException;


    @Override
    public final JsonNode root() {
        return root;
    }


    @Override
    public final Object attach() {
        return attach;
    }

    public static JsonNode nullNode(JsonNode node) {
        return node != null ? node : NullNode.getInstance();
    }

    public int getCurrentPc() {
        return 0;
    }
}