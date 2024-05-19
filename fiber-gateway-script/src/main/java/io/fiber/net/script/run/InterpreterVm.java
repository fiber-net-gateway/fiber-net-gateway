package io.fiber.net.script.run;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IteratorNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.parse.Compiled;

public class InterpreterVm extends AbstractVm {

    public static final int INSTRUMENT_LEN = 8;
    public static final int ITERATOR_LEN = 12;
    public static final int ITERATOR_OFF = INSTRUMENT_LEN + ITERATOR_LEN;
    public static final int MAX_ITERATOR_VAR = (1 << ITERATOR_LEN) - 1;


    private final int[] code;
    private final JsonNode[] stack;
    private final JsonNode[] varTable;
    private final Object[] extVal;
    private final long[] pos;
    private final int[] expIns;

    private int sp;
    private int pc;
    private ArrayNode spreadArgs;
    private int argOff;
    private int argCnt;

    private void setArgsForCtx(int off, int count) {
        spreadArgs = null;
        argOff = off;
        argCnt = count;
    }

    private void setArgsForCtx(ArrayNode args) {
        sp--;
        spreadArgs = args;
    }

    @Override
    public JsonNode getArgVal(int idx) {
        ArrayNode args = spreadArgs;
        if (args != null) {
            return args.path(idx);
        }
        return stack[argOff + idx];
    }

    @Override
    public int getArgCnt() {
        ArrayNode args = spreadArgs;
        if (args != null) {
            return args.size();
        }
        return argCnt;
    }

    @Override
    public int getCurrentPc() {
        return pc - 1;
    }

    public InterpreterVm(Compiled compiled, JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) {
        super(root, attach, resultEmitter);
        this.code = compiled.getCodes();
        this.extVal = compiled.getOperands();
        this.stack = new JsonNode[compiled.getStackSize()];
        this.varTable = new JsonNode[compiled.getVarTableSize()];
        this.pos = compiled.getPos();
        this.expIns = compiled.getExpIns();
    }


    @Override
    protected void resumeForIterate() {
        fillParamForResume();
    }

    /**
     * @return 异常结果 true
     */
    private boolean fillParamForResume() {
        int s = state;
        if (s != STAT_RETURN && s != STAT_THROW) {
            throw new IllegalStateException("no param in resume");
        }
        spreadArgs = null;
        if (s == STAT_RETURN) {
            stack[sp++] = rtValue;
            state = STAT_RUNNING;
            return false;
        } else {
            return catchForException(pc - 1);
        }
    }

    public JsonNode getResultNow() throws Throwable {
        switch (this.state) {
            case STAT_END_SEC: {
                int sp = this.sp;
                return sp > 0 ? stack[sp - 1] : null;
            }
            case STAT_END_ERR:
                assert rtError != null;
                throw rtError;
            default:
                throw new IllegalStateException("vm not end");
        }
    }

    @Override
    protected void run() {
        int[] code = this.code;
        Object[] extVal = this.extVal;
        JsonNode[] stack = this.stack;
        JsonNode[] varTable = this.varTable;
        int sp = this.sp;
        int pc = this.pc;
        try {
            for (int cLen = code.length; pc < cLen; ) {
                try {
                    int i = code[pc++];
                    switch (i & 0xFF) {
                        case Code.NOOP:
                            break;
                        case Code.LOAD_CONST:
                            stack[sp++] = ((JsonNode) extVal[i >>> 8]).deepCopy();
                            break;
                        case Code.LOAD_ROOT:
                            stack[sp++] = root;
                            break;
                        case Code.DUMP:
                            stack[sp] = stack[sp - 1];
                            ++sp;
                            break;
                        case Code.POP:
                            --sp;
                            break;

                        case Code.LOAD_VAR:
                            stack[sp++] = varTable[i >>> 8];
                            break;
                        case Code.STORE_VAR:
                            varTable[i >>> 8] = stack[--sp];
                            break;

                        case Code.BOP_PLUS:
                            --sp;
                            stack[sp - 1] = Binaries.plus(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MINUS:
                            --sp;
                            stack[sp - 1] = Binaries.minus(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MULTIPLY:
                            --sp;
                            stack[sp - 1] = Binaries.multiply(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_DIVIDE:
                            --sp;
                            stack[sp - 1] = Binaries.divide(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MOD:
                            --sp;
                            stack[sp - 1] = Binaries.modulo(stack[sp - 1], stack[sp]);
                            break;
                        // relation
                        case Code.BOP_MATCH:
                            --sp;
                            stack[sp - 1] = Binaries.matches(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_LT:
                            --sp;
                            stack[sp - 1] = Binaries.lt(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_LTE:
                            --sp;
                            stack[sp - 1] = Binaries.lte(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_GT:
                            --sp;
                            stack[sp - 1] = Binaries.gt(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_GTE:
                            --sp;
                            stack[sp - 1] = Binaries.gte(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_EQ:
                            --sp;
                            stack[sp - 1] = Binaries.eq(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_SEQ:
                            --sp;
                            stack[sp - 1] = Binaries.seq(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_NE:
                            --sp;
                            stack[sp - 1] = Binaries.ne(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_SNE:
                            --sp;
                            stack[sp - 1] = Binaries.sne(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_IN:
                            --sp;
                            stack[sp - 1] = Binaries.in(stack[sp - 1], stack[sp]);
                            break;

                        case Code.UNARY_PLUS:
                            stack[sp - 1] = Unaries.plus(stack[sp - 1]);
                            break;
                        case Code.UNARY_MINUS:
                            stack[sp - 1] = Unaries.minus(stack[sp - 1]);
                            break;
                        case Code.UNARY_NEG:
                            stack[sp - 1] = Unaries.neg(stack[sp - 1]);
                            break;
                        case Code.UNARY_TYPEOF:
                            stack[sp - 1] = Unaries.typeof(stack[sp - 1]);
                            break;

                        case Code.NEW_OBJECT:
                            stack[sp++] = JsonUtil.createObjectNode();
                            break;
                        case Code.NEW_ARRAY:
                            stack[sp++] = JsonUtil.createArrayNode();
                            break;

                        case Code.EXP_OBJECT:
                            --sp;
                            Access.expandObject(stack[sp - 1], stack[sp]);
                            break;
                        case Code.EXP_ARRAY:
                            --sp;
                            Access.expandArray(stack[sp - 1], stack[sp]);
                            break;
                        case Code.PUSH_ARRAY:
                            --sp;
                            Access.pushArray(stack[sp - 1], stack[sp]);
                            break;

                        case Code.IDX_SET:
                            sp -= 2;
                            stack[sp - 1] = Access.indexSet(stack[sp - 1], stack[sp], stack[sp + 1]);
                            break;
                        case Code.IDX_GET:
                            --sp;
                            stack[sp - 1] = Access.indexGet(stack[sp - 1], stack[sp]);
                            break;

                        case Code.PROP_GET: {
                            stack[sp - 1] = Access.propGet(stack[sp - 1], (String) extVal[i >>> 8]);
                            break;
                        }
                        case Code.PROP_SET: {
                            --sp;
                            stack[sp - 1] = Access.propSet(stack[sp - 1], stack[sp], (String) extVal[i >>> 8]);
                            break;
                        }

                        case Code.PROP_SET_1: {
                            --sp;
                            Access.propSet1(stack[sp - 1], stack[sp], (String) extVal[i >>> 8]);
                            break;
                        }

                        case Code.CALL_FUNC: {
                            Library.Function function = (Library.Function) extVal[i >>> 16];
                            int argC = (i >>> 8) & 0xFF;
                            sp -= argC;
                            setArgsForCtx(sp, argC);
                            stack[sp++] = nullNode(function.call(this));
                            break;
                        }
                        case Code.CALL_FUNC_SPREAD: {
                            Library.Function function = (Library.Function) extVal[i >>> 8];
                            setArgsForCtx((ArrayNode) stack[sp - 1]);
                            stack[sp - 1] = nullNode(function.call(this));
                            setArgsForCtx(null);
                            break;
                        }

                        case Code.CALL_ASYNC_FUNC: {
                            this.pc = pc;
                            Library.AsyncFunction function = (Library.AsyncFunction) extVal[i >>> 16];
                            int argC = (i >>> 8) & 0xFF;
                            sp -= argC;
                            setArgsForCtx(this.sp = sp, argC);// 这里要修正 sp 和 this.sp 和 this.pc 的值
                            if (callAsyncFunc(function)) {
                                return;
                            }
                            boolean exp = fillParamForResume();
                            sp = this.sp;
                            pc = this.pc;
                            if (exp) {
                                return;
                            }
                            break;
                        }
                        case Code.CALL_ASYNC_FUNC_SPREAD: {
                            this.pc = pc;
                            Library.AsyncFunction function = (Library.AsyncFunction) extVal[i >>> 8];
                            sp--;
                            setArgsForCtx((ArrayNode) stack[this.sp = sp]);// 这里要修正 sp 和 this.sp 的值
                            if (callAsyncFunc(function)) {
                                return;
                            }
                            boolean exp = fillParamForResume();
                            sp = this.sp;
                            pc = this.pc;
                            if (exp) {
                                return;
                            }
                            break;
                        }

                        case Code.CALL_CONST: {
                            stack[sp++] = nullNode(((Library.Constant) extVal[i >>> 8]).get(this));
                            break;
                        }
                        case Code.CALL_ASYNC_CONST: {
                            this.pc = pc;
                            Library.AsyncConstant constant = (Library.AsyncConstant) extVal[i >>> 8];
                            if (callAsyncConst(constant)) {
                                return;
                            }
                            boolean exp = fillParamForResume();
                            sp = this.sp;
                            pc = this.pc;
                            if (exp) {
                                return;
                            }
                            break;
                        }

                        case Code.JUMP:
                            pc = i >>> 8;
                            break;
                        case Code.JUMP_IF_FALSE: {
                            if (!Compares.logic(stack[--sp])) {
                                pc = i >>> 8;
                            }
                            break;
                        }
                        case Code.JUMP_IF_TRUE: {
                            if (Compares.logic(stack[--sp])) {
                                pc = i >>> 8;
                            }
                            break;
                        }

                        case Code.ITERATE_INTO: {
                            varTable[i >>> InterpreterVm.INSTRUMENT_LEN] = Unaries.iterate(stack[--sp]);
                            break;
                        }
                        case Code.ITERATE_NEXT: {
                            stack[sp++] = BooleanNode.valueOf(((IteratorNode) varTable[i >>> InterpreterVm.INSTRUMENT_LEN]).next());
                            break;
                        }
                        case Code.ITERATE_KEY: {
                            varTable[(i >>> InterpreterVm.INSTRUMENT_LEN) & MAX_ITERATOR_VAR] = ((IteratorNode) varTable[i >>> InterpreterVm.ITERATOR_OFF]).currentKey();
                            break;
                        }
                        case Code.ITERATE_VALUE: {
                            varTable[(i >>> InterpreterVm.INSTRUMENT_LEN) & MAX_ITERATOR_VAR] = ((IteratorNode) varTable[i >>> InterpreterVm.ITERATOR_OFF]).currentValue();
                            break;
                        }

                        case Code.INTO_CATCH: {
                            varTable[i >>> InterpreterVm.INSTRUMENT_LEN] = errorToObj();
                            break;
                        }

                        case Code.END_RETURN: {
                            rtValue = sp > 0 ? stack[sp - 1] : null;
                            state = STAT_END_SEC;
                            return;
                        }

                        case Code.THROW_EXP: {
                            rtError = objToError(stack[--sp]);
                            if (catchForException(pc - 1)) {
                                return;
                            }
                            pc = this.pc;
                            sp = this.sp;
                            break;
                        }
                        default:
                            throw new Error("unknown code:" + i);
                    }
                } catch (ScriptExecException e) {
                    sp = 0;
                    rtError = e;
                    if (catchForException(pc - 1)) {
                        return;
                    }
                    pc = this.pc;
                }
            }
        } finally {
            this.pc = pc;
            this.sp = sp;
        }
        throw new IllegalStateException("[BUG] no return ???");
    }


    private int searchCatch(int epc) {
        int[] expIns;
        int len, l, r;
        if ((expIns = this.expIns) == null || epc < expIns[l = 0] || expIns[(r = len = expIns.length >> 1) - 1] <= epc) {
            return -1;
        }

        if (len <= 8) { // too small ，linear search
            for (int i = 1; i < len; i++) {
                if (expIns[i] >= epc) {
                    return expIns[i - 1 + len];
                }
            }
        }
        while (l < r) {// binary search
            int m;
            int mv;
            if (epc < (mv = expIns[m = (l + r) >> 1])) {
                r = m;
            } else if (epc > mv) {
                l = m + 1;
            } else {
                return expIns[epc + len];
            }
        }
        return expIns[l - 1 + len];
    }

    private boolean catchForException(int epc) {
        assert rtError != null;
        rtError.setPos(pos[epc]);
        sp = 0;
        int cpc;
        if ((cpc = searchCatch(epc)) < 0) {
            state = STAT_END_ERR;
            return true;
        }
        this.pc = cpc;
        state = STAT_RUNNING;
        return false;
    }

    public static InterpreterVm createFromCompiled(Compiled compiled, JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) {
        return new InterpreterVm(compiled, root, attach, resultEmitter);
    }
}
