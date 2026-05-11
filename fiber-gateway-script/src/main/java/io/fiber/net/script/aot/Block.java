package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;
import io.fiber.net.script.run.InterpreterVm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Block {

    private final List<Instruction> instructions = new ArrayList<>();

    final List<Edge> predecessors = new ArrayList<>();
    final List<Edge> successors = new ArrayList<>();
    final int startPc;
    int endPc; // exclude. immutable
    private List<SsaValue> phiValues;

    int inputStackSize; // stackSize when enter block
    int legacyStackSize; // stackSize legacy (do not contain stack size produced by this) when lease block
    int outputStackSize; // produced stackSize by this block
    private int requiredInputStackSize;
    private int stackDelta;
    private List<MaybePhi> maybePhis;
    private SsaValue[] exitStack;
    private SsaValue[] exitVars;

    public Block(int startPc) {
        this.startPc = startPc;
    }

    void prepareStackShape(Compiled compiled) {
        int depth = 0;
        int minDepth = 0;
        int[] codes = compiled.getCodes();
        for (int i = startPc; i < endPc; i++) {
            int code = codes[i];
            int ins = code & 0xFF;
            int consume;
            int produce;
            switch (ins) {
                case Code.NOOP:
                case Code.JUMP:
                case Code.ITERATE_NEXT:
                case Code.ITERATE_KEY:
                case Code.ITERATE_VALUE:
                case Code.INTO_CATCH:
                case Code.END_RETURN:
                    consume = 0;
                    produce = ins == Code.ITERATE_NEXT ? 1 : 0;
                    break;
                case Code.LOAD_CONST:
                case Code.LOAD_ROOT:
                case Code.LOAD_VAR:
                case Code.NEW_OBJECT:
                case Code.NEW_ARRAY:
                case Code.CALL_CONST:
                case Code.CALL_ASYNC_CONST:
                    consume = 0;
                    produce = 1;
                    break;
                case Code.DUMP:
                    consume = 1;
                    produce = 2;
                    break;
                case Code.POP:
                case Code.STORE_VAR:
                case Code.UNARY_PLUS:
                case Code.UNARY_MINUS:
                case Code.UNARY_NEG:
                case Code.UNARY_TYPEOF:
                case Code.CALL_FUNC_SPREAD:
                case Code.CALL_ASYNC_FUNC_SPREAD:
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:
                case Code.ITERATE_INTO:
                case Code.THROW_EXP:
                    consume = 1;
                    produce = isUnary(ins) || ins == Code.CALL_FUNC_SPREAD || ins == Code.CALL_ASYNC_FUNC_SPREAD ? 1 : 0;
                    break;
                case Code.EXP_OBJECT:
                case Code.EXP_ARRAY:
                case Code.PUSH_ARRAY:
                case Code.IDX_GET:
                case Code.PROP_SET:
                case Code.PROP_SET_1:
                case Code.BOP_PLUS:
                case Code.BOP_MINUS:
                case Code.BOP_MULTIPLY:
                case Code.BOP_DIVIDE:
                case Code.BOP_MOD:
                case Code.BOP_MATCH:
                case Code.BOP_LT:
                case Code.BOP_LTE:
                case Code.BOP_GT:
                case Code.BOP_GTE:
                case Code.BOP_EQ:
                case Code.BOP_SEQ:
                case Code.BOP_NE:
                case Code.BOP_SNE:
                case Code.BOP_IN:
                    consume = 2;
                    produce = 1;
                    break;
                case Code.PROP_GET:
                    consume = 1;
                    produce = 1;
                    break;
                case Code.IDX_SET:
                case Code.IDX_SET_1:
                    consume = 3;
                    produce = 1;
                    break;
                case Code.CALL_FUNC:
                case Code.CALL_ASYNC_FUNC:
                    consume = (code >>> 8) & 0xFF;
                    produce = 1;
                    break;
                default:
                    throw new IllegalStateException("unknown code:" + code);
            }
            depth -= consume;
            if (depth < minDepth) {
                minDepth = depth;
            }
            depth += produce;
        }
        requiredInputStackSize = -minDepth;
        stackDelta = depth;
        inputStackSize = -1;
        outputStackSize = -1;
    }

    void setFallbackInputStackSize() {
        if (inputStackSize < 0) {
            inputStackSize = requiredInputStackSize;
            outputStackSize = inputStackSize + stackDelta;
        }
    }

    boolean mergeInputStackSize(int stackSize) {
        if (stackSize < requiredInputStackSize) {
            throw new IllegalStateException("stack underflow at block " + startPc);
        }
        if (inputStackSize < 0) {
            inputStackSize = stackSize;
            outputStackSize = stackSize + stackDelta;
            return true;
        }
        if (inputStackSize != stackSize) {
            throw new IllegalStateException("inconsistent stack size at block " + startPc);
        }
        return false;
    }

    void simulate(Compiled compiled, Cfg cfg) {
        instructions.clear();
        maybePhis = null;
        setFallbackInputStackSize();

        Frame frame = new Frame(compiled.getStackSize(), compiled.getVarTableSize(), inputStackSize);
        for (int i = 0; i < inputStackSize; i++) {
            frame.stack[i] = newMaybePhi(startPc, i, true).getResult();
            frame.legacy[i] = true;
        }

        int[] codes = compiled.getCodes();
        Object[] operands = compiled.getOperands();
        for (int i = startPc; i < endPc; i++) {
            int code = codes[i];
            int ins = code & 0xFF;
            switch (ins) {
                case Code.NOOP:
                    break;
                case Code.LOAD_CONST:
                    push(frame, new LoadConst(this, i, (ValueNode) operands[code >>> 8]));
                    break;
                case Code.LOAD_ROOT:
                    push(frame, new LoadRoot(this, i));
                    break;
                case Code.DUMP:
                    frame.stack[frame.sp] = frame.stack[frame.sp - 1];
                    frame.legacy[frame.sp] = frame.legacy[frame.sp - 1];
                    frame.sp++;
                    break;
                case Code.POP:
                    frame.sp--;
                    break;
                case Code.LOAD_VAR:
                    frame.stack[frame.sp] = loadVar(frame, i, code >>> 8);
                    frame.legacy[frame.sp++] = false;
                    break;
                case Code.STORE_VAR:
                    frame.vars[code >>> 8] = pop(frame);
                    break;
                case Code.NEW_OBJECT:
                    push(frame, new NewObj(this, i));
                    break;
                case Code.NEW_ARRAY:
                    push(frame, new NewArr(this, i));
                    break;
                case Code.EXP_OBJECT: {
                    SsaValue addition = pop(frame);
                    emit(new ExpandObj(this, i, peek(frame), addition));
                    break;
                }
                case Code.EXP_ARRAY: {
                    SsaValue addition = pop(frame);
                    emit(new ExpandArr(this, i, peek(frame), addition));
                    break;
                }
                case Code.PUSH_ARRAY: {
                    SsaValue addition = pop(frame);
                    emit(new PushArr(this, i, peek(frame), addition));
                    break;
                }
                case Code.IDX_GET: {
                    SsaValue key = pop(frame);
                    SsaValue owner = pop(frame);
                    push(frame, new IndexGet(this, i, owner, key));
                    break;
                }
                case Code.IDX_SET: {
                    SsaValue alien = pop(frame);
                    SsaValue key = pop(frame);
                    SsaValue owner = pop(frame);
                    push(frame, new IndexSet(this, i, owner, key, alien));
                    break;
                }
                case Code.IDX_SET_1: {
                    SsaValue alien = pop(frame);
                    SsaValue key = pop(frame);
                    SsaValue owner = pop(frame);
                    push(frame, new IndexSet1(this, i, owner, key, alien));
                    break;
                }
                case Code.PROP_GET: {
                    SsaValue owner = pop(frame);
                    push(frame, new PropGet(this, i, owner, (String) operands[code >>> 8]));
                    break;
                }
                case Code.PROP_SET: {
                    SsaValue alien = pop(frame);
                    SsaValue owner = pop(frame);
                    push(frame, new PropSet(this, i, owner, (String) operands[code >>> 8], alien));
                    break;
                }
                case Code.PROP_SET_1: {
                    SsaValue alien = pop(frame);
                    SsaValue owner = pop(frame);
                    push(frame, new PropSet1(this, i, owner, (String) operands[code >>> 8], alien));
                    break;
                }
                case Code.BOP_PLUS:
                case Code.BOP_MINUS:
                case Code.BOP_MULTIPLY:
                case Code.BOP_DIVIDE:
                case Code.BOP_MOD:
                case Code.BOP_MATCH:
                case Code.BOP_LT:
                case Code.BOP_LTE:
                case Code.BOP_GT:
                case Code.BOP_GTE:
                case Code.BOP_EQ:
                case Code.BOP_SEQ:
                case Code.BOP_NE:
                case Code.BOP_SNE:
                case Code.BOP_IN: {
                    SsaValue right = pop(frame);
                    SsaValue left = pop(frame);
                    push(frame, new Binary(this, i, toBinaryOp(ins), left, right));
                    break;
                }
                case Code.UNARY_PLUS:
                case Code.UNARY_MINUS:
                case Code.UNARY_NEG:
                case Code.UNARY_TYPEOF:
                    push(frame, new Unary(this, i, toUnaryOp(ins), pop(frame)));
                    break;
                case Code.CALL_FUNC: {
                    SsaValue[] args = popArgs(frame, (code >>> 8) & 0xFF);
                    push(frame, new CallFunc(this, i, (Library.Function) operands[code >>> 16], false, args));
                    break;
                }
                case Code.CALL_FUNC_SPREAD:
                    push(frame, new CallFunc(this, i, (Library.Function) operands[code >>> 8], true,
                            new SsaValue[]{pop(frame)}));
                    break;
                case Code.CALL_ASYNC_FUNC: {
                    SsaValue[] args = popArgs(frame, (code >>> 8) & 0xFF);
                    push(frame, new CallAsyncFunc(this, i, (Library.AsyncFunction) operands[code >>> 16], false, args));
                    break;
                }
                case Code.CALL_ASYNC_FUNC_SPREAD:
                    push(frame, new CallAsyncFunc(this, i, (Library.AsyncFunction) operands[code >>> 8], true,
                            new SsaValue[]{pop(frame)}));
                    break;
                case Code.CALL_CONST:
                    push(frame, new CallConst(this, i, (Library.Constant) operands[code >>> 8]));
                    break;
                case Code.CALL_ASYNC_CONST:
                    push(frame, new CallAsyncConst(this, i, (Library.AsyncConstant) operands[code >>> 8]));
                    break;
                case Code.JUMP:
                    emit(new Jump(this, i, cfg.mustGetBlock(code >>> 8)));
                    break;
                case Code.JUMP_IF_FALSE:
                    emit(new JumpIfFalse(this, i, cfg.mustGetBlock(code >>> 8), pop(frame)));
                    break;
                case Code.JUMP_IF_TRUE:
                    emit(new JumpIfTrue(this, i, cfg.mustGetBlock(code >>> 8), pop(frame)));
                    break;
                case Code.ITERATE_INTO:
                    frame.vars[code >>> InterpreterVm.INSTRUMENT_LEN] =
                            emitExpr(new Unary(this, i, Unary.Op.ITERATE_INTO, pop(frame))).getResult();
                    break;
                case Code.ITERATE_NEXT:
                    push(frame, new Unary(this, i, Unary.Op.ITERATE_NEXT,
                            loadVar(frame, i, code >>> InterpreterVm.INSTRUMENT_LEN)));
                    break;
                case Code.ITERATE_KEY:
                    frame.vars[(code >>> InterpreterVm.INSTRUMENT_LEN) & InterpreterVm.MAX_ITERATOR_VAR] =
                            emitExpr(new Unary(this, i, Unary.Op.ITERATE_KEY,
                                    loadVar(frame, i, code >>> InterpreterVm.ITERATOR_OFF))).getResult();
                    break;
                case Code.ITERATE_VALUE:
                    frame.vars[(code >>> InterpreterVm.INSTRUMENT_LEN) & InterpreterVm.MAX_ITERATOR_VAR] =
                            emitExpr(new Unary(this, i, Unary.Op.ITERATE_VALUE,
                                    loadVar(frame, i, code >>> InterpreterVm.ITERATOR_OFF))).getResult();
                    break;
                case Code.INTO_CATCH:
                    emit(new IntoCatch(this, i));
                    frame.vars[code >>> InterpreterVm.INSTRUMENT_LEN] =
                            newMaybePhi(i, code >>> InterpreterVm.INSTRUMENT_LEN, false).getResult();
                    break;
                case Code.THROW_EXP:
                    emit(new Throw(this, i, pop(frame)));
                    break;
                case Code.END_RETURN:
                    emit(frame.sp > 0 ? new Ret(this, i, frame.stack[frame.sp - 1]) : new RetV(this, i));
                    break;
                default:
                    throw new IllegalStateException("unknown code:" + code);
            }
        }

        outputStackSize = frame.sp;
        legacyStackSize = 0;
        for (int i = 0; i < frame.sp; i++) {
            if (frame.legacy[i]) {
                legacyStackSize++;
            }
        }
        exitStack = new SsaValue[frame.sp];
        System.arraycopy(frame.stack, 0, exitStack, 0, frame.sp);
        exitVars = frame.vars;
    }

    public void addPhi(Phi phi) {
        if (phiValues == null) {
            phiValues = new ArrayList<>();
        }
        phiValues.add(new SsaValue(phi));
    }

    public Phi newPhi() {
        return new Phi(this, startPc);
    }

    public List<SsaValue> getPhiValues() {
        return phiValues;
    }

    public List<Edge> getPredecessors() {
        return predecessors;
    }

    public List<Edge> getSuccessors() {
        return successors;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public List<MaybePhi> getMaybePhis() {
        if (maybePhis == null) {
            return Collections.emptyList();
        }
        return maybePhis;
    }

    public SsaValue[] getExitStack() {
        return exitStack;
    }

    public SsaValue[] getExitVars() {
        return exitVars;
    }

    private MaybePhi newMaybePhi(int pc, int idx, boolean stack) {
        MaybePhi maybePhi = new MaybePhi(this, pc, idx, stack);
        if (maybePhis == null) {
            maybePhis = new ArrayList<>();
        }
        maybePhis.add(maybePhi);
        return maybePhi;
    }

    private static boolean isUnary(int ins) {
        return ins == Code.UNARY_PLUS || ins == Code.UNARY_MINUS ||
                ins == Code.UNARY_NEG || ins == Code.UNARY_TYPEOF;
    }

    private SsaValue loadVar(Frame frame, int pc, int idx) {
        SsaValue var = frame.vars[idx];
        if (var != null) {
            return var;
        }
        var = newMaybePhi(pc, idx, false).getResult();
        frame.vars[idx] = var;
        return var;
    }

    private SsaValue pop(Frame frame) {
        return frame.stack[--frame.sp];
    }

    private SsaValue peek(Frame frame) {
        return frame.stack[frame.sp - 1];
    }

    private SsaValue[] popArgs(Frame frame, int argCount) {
        frame.sp -= argCount;
        SsaValue[] args = new SsaValue[argCount];
        System.arraycopy(frame.stack, frame.sp, args, 0, argCount);
        return args;
    }

    private void push(Frame frame, Expr expr) {
        frame.stack[frame.sp] = emitExpr(expr).getResult();
        frame.legacy[frame.sp++] = false;
    }

    private Expr emitExpr(Expr expr) {
        emit(expr);
        return expr;
    }

    private void emit(Instruction instruction) {
        instructions.add(instruction);
    }

    private static Binary.Op toBinaryOp(int ins) {
        return Binary.Op.values()[ins - Code.BOP_PLUS];
    }

    private static Unary.Op toUnaryOp(int ins) {
        return Unary.Op.values()[ins - Code.UNARY_PLUS];
    }

    private static class Frame {
        final SsaValue[] stack;
        final boolean[] legacy;
        final SsaValue[] vars;
        int sp;

        Frame(int stackSize, int varSize, int sp) {
            this.stack = new SsaValue[stackSize];
            this.legacy = new boolean[stackSize];
            this.vars = new SsaValue[varSize];
            this.sp = sp;
        }
    }
}
