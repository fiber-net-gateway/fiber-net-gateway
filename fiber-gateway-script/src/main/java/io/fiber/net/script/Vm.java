package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.internal.MaybeSubject;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.std.Compares;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class Vm implements ExecutionContext {

    private abstract static class Scope {
        final Scope outer;

        private Scope(Scope outer) {
            this.outer = outer;
        }

        abstract boolean dumpToStack(int sp, JsonNode[] stack);
    }

    private static class ObjItrScope extends Scope {

        private ObjItrScope(Scope outer, Iterator<Map.Entry<String, JsonNode>> iterator) {
            super(outer);
            this.iterator = iterator;
        }

        private final Iterator<Map.Entry<String, JsonNode>> iterator;

        @Override
        boolean dumpToStack(int sp, JsonNode[] stack) {
            if (iterator.hasNext()) {
                Map.Entry<String, JsonNode> next = iterator.next();
                stack[sp] = TextNode.valueOf(next.getKey());
                stack[sp + 1] = next.getValue();
                return true;
            }
            return false;
        }
    }

    private static class ArrItrScope extends Scope {

        private ArrItrScope(Scope outer, Iterator<JsonNode> iterator) {
            super(outer);
            this.iterator = iterator;
        }

        private int idx;
        private final Iterator<JsonNode> iterator;

        @Override
        boolean dumpToStack(int sp, JsonNode[] stack) {
            if (iterator.hasNext()) {
                JsonNode next = iterator.next();
                stack[sp] = IntNode.valueOf(idx++);
                stack[sp + 1] = next;
                return true;
            }
            return false;
        }
    }

    private static class TryScope extends Scope {

        final int catchPc;

        private TryScope(Scope outer, int catchPc) {
            super(outer);
            this.catchPc = catchPc;
        }

        @Override
        boolean dumpToStack(int sp, JsonNode[] stack) {
            throw new UnsupportedOperationException("unsupported");
        }
    }

    private static class ResultSubject extends MaybeSubject<JsonNode> {
        @Override
        protected void onDismissClear(JsonNode value) {
        }
    }

    private static final int STAT_INIT = 0;

    private static final int STAT_RUNNING = 1;
    private static final int STAT_ASYNC = 2;

    private static final int STAT_RETURN = 4;
    private static final int STAT_THROW = 5;

    private static final int STAT_END_SEC = 7;
    private static final int STAT_END_ERR = 8;


    private final int[] code;
    private int pc;

    private final JsonNode[] stack;
    private final JsonNode[] varTable;
    private int sp;

    private final Object[] extVal;

    private final Object attach;
    private final JsonNode root;

    private Object fcCn;
    private int state;
    private Object rtError;
    private final MaybeSubject<JsonNode> result = new ResultSubject();


    private Scope itrScope;

    public int getCurrentPc() {
        return pc - 1;
    }

    public Vm(int[] code, Object[] extVal, int maxStack, int maxVarCapacity, JsonNode root, Object attach) {
        this.code = code;
        this.extVal = extVal;
        this.attach = attach;
        this.root = root;
        this.stack = new JsonNode[maxStack];
        this.varTable = new JsonNode[maxVarCapacity];
    }

    public Maybe<JsonNode> exec() {
        iterate();
        return result;
    }

    private void iterate() {
        assert state != STAT_ASYNC;
        if (state == STAT_THROW) {
            catchForException();
        }
        if (state == STAT_INIT || state == STAT_RETURN) {
            state = STAT_RUNNING;
        }

        if (state == STAT_RUNNING) {
            run();
        }
        if (state == STAT_END_SEC) {
            int sp = this.sp;
            if (sp > 0) {
                result.onSuccess(stack[sp - 1]);
            } else {
                result.onComplete();
            }
        } else if (state == STAT_END_ERR) {
            assert rtError != null;
            result.onError(objToError());
        }
    }

    public boolean isEnd() {
        int state = this.state;
        return state == STAT_END_SEC || state == STAT_END_ERR;
    }

    public JsonNode getResultNow() throws Throwable {
        switch (this.state) {
            case STAT_END_SEC: {
                int sp = this.sp;
                return sp > 0 ? stack[sp - 1] : null;
            }
            case STAT_END_ERR:
                throw objToError();
            default:
                throw new IllegalStateException("vm not end");
        }
    }

    private void run() {
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
                        case Code.SWAP: {
                            JsonNode t = stack[sp - 1];
                            stack[sp - 1] = stack[sp - 2];
                            stack[sp - 2] = t;
                            break;
                        }
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
                        case Code.STORE_VAR_1:
                            varTable[i >>> 8] = stack[sp - 1];
                            break;

                        case Code.BOP_PLUS:
                            --sp;
                            stack[sp - 1] = BinaryOperator.plus(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MINUS:
                            --sp;
                            stack[sp - 1] = BinaryOperator.minus(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MULTIPLY:
                            --sp;
                            stack[sp - 1] = BinaryOperator.multiply(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_DIVIDE:
                            --sp;
                            stack[sp - 1] = BinaryOperator.divide(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_MOD:
                            --sp;
                            stack[sp - 1] = BinaryOperator.modulo(stack[sp - 1], stack[sp]);
                            break;
                        // relation
                        case Code.BOP_MATCH:
                            --sp;
                            stack[sp - 1] = BinaryOperator.matches(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_LT:
                            --sp;
                            stack[sp - 1] = BinaryOperator.lt(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_LTE:
                            --sp;
                            stack[sp - 1] = BinaryOperator.lte(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_GT:
                            --sp;
                            stack[sp - 1] = BinaryOperator.gt(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_GTE:
                            --sp;
                            stack[sp - 1] = BinaryOperator.gte(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_EQ:
                            --sp;
                            stack[sp - 1] = BinaryOperator.eq(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_SEQ:
                            --sp;
                            stack[sp - 1] = BinaryOperator.seq(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_NE:
                            --sp;
                            stack[sp - 1] = BinaryOperator.ne(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_SNE:
                            --sp;
                            stack[sp - 1] = BinaryOperator.sne(stack[sp - 1], stack[sp]);
                            break;
                        case Code.BOP_IN:
                            --sp;
                            stack[sp - 1] = BinaryOperator.in(stack[sp - 1], stack[sp]);
                            break;

                        case Code.UNARY_PLUS:
                            stack[sp - 1] = Unary.plus(stack[sp - 1]);
                            break;
                        case Code.UNARY_MINUS:
                            stack[sp - 1] = Unary.minus(stack[sp - 1]);
                            break;
                        case Code.UNARY_NEG:
                            stack[sp - 1] = Unary.not(stack[sp - 1]);
                            break;
                        case Code.UNARY_TYPEOF:
                            stack[sp - 1] = Unary.typeof(stack[sp - 1]);
                            break;

                        case Code.NEW_OBJECT:
                            stack[sp++] = JsonUtil.createObjectNode();
                            break;
                        case Code.NEW_ARRAY:
                            stack[sp++] = JsonUtil.createArrayNode();
                            break;

                        case Code.EXP_OBJECT:
                            --sp;
                            stack[sp - 1] = Access.expandObject(stack[sp], stack[sp - 1]);
                            break;
                        case Code.EXP_ARRAY:
                            --sp;
                            stack[sp - 1] = Access.expandArray(stack[sp], stack[sp - 1]);
                            break;
                        case Code.PUSH_ARRAY:
                            --sp;
                            stack[sp - 1] = Access.pushArray(stack[sp], stack[sp - 1]);
                            break;

                        case Code.IDX_SET:
                            sp -= 2;
                            stack[sp - 1] = Access.indexSet(stack[sp + 1], stack[sp], stack[sp - 1]);
                            break;
                        case Code.IDX_GET:
                            --sp;
                            stack[sp - 1] = Access.indexGet(stack[sp], stack[sp - 1]);
                            break;

                        case Code.PROP_GET: {
                            stack[sp - 1] = Access.propGet((String) extVal[i >>> 8], stack[sp - 1]);
                            break;
                        }
                        case Code.PROP_SET: {
                            --sp;
                            stack[sp - 1] = Access.propSet(stack[sp], (String) extVal[i >>> 8], stack[sp - 1]);
                            break;
                        }

                        case Code.PROP_SET_1: {
                            --sp;
                            Access.propSet(stack[sp], (String) extVal[i >>> 8], stack[sp - 1]);
                            break;
                        }

                        case Code.CALL_FUNC: {
                            Library.Function function = (Library.Function) extVal[i >>> 16];
                            int argC = (i >>> 8) & 0xFF;
                            JsonNode[] nodes = Arrays.copyOfRange(stack, sp - argC, sp);
                            sp -= argC;
                            this.sp = sp;
                            if (callFunc(function, argC == 0 ? AstUtils.EMPTY_JSON_NODES : nodes)) {
                                // async
                                return;
                            }
                            sp = this.sp;
                            break;
                        }
                        case Code.CALL_FUNC_SPREAD: {
                            Library.Function function = (Library.Function) extVal[i >>> 8];
                            JsonNode[] args = toArgs((ArrayNode) stack[--sp]);
                            this.sp = sp;
                            if (callFunc(function, args)) {
                                //async
                                return;
                            }
                            sp = this.sp;
                            break;
                        }
                        case Code.CALL_CONST: {
                            this.sp = sp;
                            if (callConst((Library.Constant) extVal[i >>> 8])) {
                                //async
                                return;
                            }
                            sp = this.sp;
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
                        case Code.LOGICAL_AND: {
                            if (Compares.logic(stack[sp - 1])) {
                                sp--;
                            } else {
                                pc = i >>> 8;
                            }
                            break;
                        }
                        case Code.LOGICAL_OR: {
                            if (Compares.logic(stack[sp - 1])) {
                                pc = i >>> 8;
                            } else {
                                sp--;
                            }
                            break;
                        }

                        case Code.INTO_ITERATE: {
                            JsonNode node = stack[--sp];
                            if (node.isObject()) {
                                itrScope = new ObjItrScope(itrScope, node.fields());
                            } else {
                                itrScope = new ArrItrScope(itrScope, node.iterator());
                            }
                            break;
                        }
                        case Code.NEXT_ITERATE: {
                            assert itrScope != null;
                            if (itrScope.dumpToStack(sp, stack)) {
                                sp += 2;
                            } else {
                                pc = i >>> 8;
                            }
                            break;
                        }

                        case Code.END_ITERATE:
                        case Code.END_TRY: {
                            assert itrScope != null;
                            itrScope = itrScope.outer;
                            break;
                        }

                        case Code.INTO_TRY: {
                            itrScope = new TryScope(itrScope, i >>> 8);
                            break;
                        }

                        case Code.INTO_CATCH: {
                            stack[sp++] = errorToObj();
                            break;
                        }

                        case Code.END_RETURN: {
                            state = STAT_END_SEC;
                            return;
                        }

                        case Code.THROW_EXP: {
                            rtError = stack[--sp];
                            sp = 0;
                            if (catchForException()) {
                                return;
                            }
                            pc = this.pc;
                            break;
                        }
                        default:
                            throw new Error("unknown code:" + i);
                    }
                } catch (ScriptExecException e) {
                    rtError = e;
                    sp = 0;
                    if (catchForException()) {
                        return;
                    }
                    pc = this.pc;
                }
            }
        } finally {
            this.pc = pc;
            this.sp = sp;
        }
        state = STAT_END_SEC;
    }

    private Throwable objToError() {
        Object e = rtError;
        assert e != null;
        if (e instanceof Throwable) {
            return (Throwable) e;
        }
        assert e instanceof JsonNode;

        JsonNode node = (JsonNode) e;
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
        return new ScriptExecException(name, code, msg);
    }

    private JsonNode errorToObj() {
        Object e = rtError;
        assert e != null;
        rtError = null;

        if (e instanceof ScriptExecException) {
            ScriptExecException error = (ScriptExecException) e;
            ObjectNode node = JsonUtil.MAPPER.createObjectNode();
            node.put("status", error.getCode());
            node.put("name", error.getErrorName());
            node.put("message", error.getMessage());
            return node;
        } else {
            assert e instanceof JsonNode;
            return (ObjectNode) e;
        }
    }

    private boolean catchForException() {
        assert rtError != null;
        Scope scope = itrScope;
        while (scope != null) {
            if (scope instanceof TryScope) {
                TryScope tryScope = (TryScope) scope;
                assert tryScope.catchPc != 0;
                pc = tryScope.catchPc;
                itrScope = tryScope.outer;
                state = STAT_RUNNING;
                return false;
            }
            scope = scope.outer;
        }
        itrScope = null;
        state = STAT_END_ERR;
        return true;
    }

    private static JsonNode[] toArgs(ArrayNode spread) {
        int size = spread.size();
        if (size == 0) {
            return AstUtils.EMPTY_JSON_NODES;
        }
        JsonNode[] nodes = new JsonNode[size];
        int i = 0;
        for (JsonNode jsonNode : spread) {
            nodes[i++] = jsonNode;
        }
        return nodes;
    }

    private boolean callFunc(Library.Function function, JsonNode[] args) throws ScriptExecException {
        fcCn = function;
        function.call(this, args);
        if (fcCn == function) {
            if (function.isConstExpr()) {
                throw new IllegalStateException("async function cannot mark as constant");
            }
            // 没有调用 throw 和 return
            state = STAT_ASYNC;
            return true;
        }
        if (state == STAT_THROW) {
            ScriptExecException e = (ScriptExecException) rtError;
            rtError = null;
            assert e != null;
            throw e;
        }
        assert state == STAT_RETURN;
        state = STAT_RUNNING;
        return false;
    }

    private boolean callConst(Library.Constant cn) throws ScriptExecException {
        fcCn = cn;
        cn.get(this);
        if (fcCn == cn) {
            if (cn.isConstExpr()) {
                throw new IllegalStateException("async constant cannot mark as constant");
            }
            // 没有调用 throw 和 return
            state = STAT_ASYNC;
            return true;
        }
        if (state == STAT_THROW) {
            ScriptExecException e = (ScriptExecException) rtError;
            rtError = null;
            assert e != null;
            throw e;
        }
        assert state == STAT_RETURN;
        state = STAT_RUNNING;
        return false;
    }

    @Override
    public void returnVal(Library.Function fc, JsonNode value) {
        returnVal0(fc, value);
    }

    @Override
    public void throwErr(Library.Function fc, ScriptExecException error) {
        throwErr0(fc, error);
    }


    @Override
    public void returnVal(Library.Constant cn, JsonNode value) {
        returnVal0(cn, value);
    }


    @Override
    public void throwErr(Library.Constant cn, ScriptExecException error) {
        throwErr0(cn, error);
    }

    private void returnVal0(Object f, JsonNode value) {
        if (fcCn != f) {
            throw new IllegalStateException("only be return once");
        }
        fcCn = null;
        stack[sp++] = value == null ? MissingNode.getInstance() : value;
        boolean ctnRun = state == STAT_ASYNC;
        state = STAT_RETURN;
        if (ctnRun) {
            iterate();
        }
    }

    private void throwErr0(Object f, ScriptExecException error) {
        if (fcCn != f) {
            throw new IllegalStateException("only be return once");
        }
        fcCn = null;
        rtError = error;
        boolean ctnRun = state == STAT_ASYNC;
        state = STAT_THROW;
        if (ctnRun) {
            iterate();
        }
    }


    @Override
    public JsonNode root() {
        return root;
    }


    @Override
    public Object attach() {
        return attach;
    }


}
