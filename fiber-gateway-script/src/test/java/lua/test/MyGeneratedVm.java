package lua.test;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.UnsafeUtil;

public class MyGeneratedVm extends AbstractVm {
    private static ValueNode _LITERAL_0;
    private static Library.Function _FUNC_0;
    private static Library.AsyncFunction _ASYNC_FUNC_0;
    private static final long STACK_OFFSET;
    private static final long JSON_NODE_OCCUPY;
    private int asyncState;
    private int funcParamSP;
    private int funcArgc;
    private boolean spread;
    private JsonNode _stack_0;

    static {
        try {
            STACK_OFFSET = UnsafeUtil.getObjectOffset(MyGeneratedVm.class.getDeclaredField("_stack_0"));
            JSON_NODE_OCCUPY = UnsafeUtil.getJsonNodeOccupy();
        } catch (Throwable var1) {
            throw new RuntimeException(var1);
        }
    }

    public static void __INIT_OPERAND__(Object[] var0) {
        _LITERAL_0 = (ValueNode) var0[0];
        _FUNC_0 = (Library.Function) var0[1];
        _ASYNC_FUNC_0 = (Library.AsyncFunction) var0[2];
    }

    public MyGeneratedVm(JsonNode var1, Object var2) {
        super(var1, var2);
    }

    public JsonNode getArgVal(int var1) {
        if (!this.spread) {
            if (var1 >= 0 && var1 < this.funcArgc) {
                return UnsafeUtil.getJsonNodeObject(this, STACK_OFFSET + JSON_NODE_OCCUPY * (long) (var1 + this.funcParamSP));
            } else {
                throw new IllegalStateException("no arguments at :" + var1);
            }
        } else {
            return UnsafeUtil.getJsonNodeObject(this, STACK_OFFSET + JSON_NODE_OCCUPY * (long) this.funcParamSP).get(var1);
        }
    }

    public int getArgCnt() {
        return !this.spread ? this.funcArgc : UnsafeUtil.getJsonNodeObject(this, STACK_OFFSET + JSON_NODE_OCCUPY * (long) this.funcParamSP).size();
    }

    protected void run() throws ScriptExecException {
        switch (this.asyncState) {
            case 0:
                long a = 1;
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                _FUNC_0.call(this);
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                _FUNC_0.call(this);
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                _FUNC_0.call(this);
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                if (this.callAsyncFunc(_ASYNC_FUNC_0)) {
                    this.asyncState = 1;
                    return;
                }
            case 1:
                if (this.rtError != null) {
                    throw this.rtError;
                }
                a = 2;
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                _FUNC_0.call(this);
                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                _FUNC_0.call(this);

                this._stack_0 = _LITERAL_0;
                this.funcParamSP = 0;
                this.funcArgc = 1;
                this.spread = false;
                if (this.callAsyncFunc(_ASYNC_FUNC_0)) {
                    this.asyncState = 1;
                    return;
                }
            case 2:
                if (this.rtError != null) {
                    throw this.rtError;
                }

                this.rtValue = null;
                this.state = 7;
                return;
            default:
                throw new IllegalStateException("[BUG] not hit asyncState");
        }
    }

    public static void main(String[] args) {
        System.out.println("");
    }
}
