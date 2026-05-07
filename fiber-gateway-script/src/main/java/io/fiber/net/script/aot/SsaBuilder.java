package io.fiber.net.script.aot;

import io.fiber.net.script.parse.Compiled;

import java.util.*;

public final class SsaBuilder {
    private SsaValueFactory factory;
    private Cfg cfg;
    private List<SsaBlock> ssaBlocks;
    private SsaBlock[] ssaByBlockId;
    private SsaValue[] undefinedVars;
    private int varCount;

    public SsaGraph build(Compiled compiled) {
        Cfg cfg = new AotCfgBuilder().build(compiled);
        return build(cfg, compiled.getVarTableSize(), compiled.getOperands());
    }

    public SsaGraph build(Cfg cfg) {
        return build(cfg, deriveVarCount(cfg));
    }

    public SsaGraph build(Cfg cfg, int varCount) {
        return build(cfg, varCount, null);
    }

    public SsaGraph build(Cfg cfg, int varCount, Object[] constants) {
        this.factory = new SsaValueFactory();
        this.cfg = cfg;
        this.varCount = Math.max(0, varCount);
        int maxStack = cfg.codeLength() + 1;
        initBlocks(maxStack);
        initUndefinedVars();
        runWorkList();
        return new SsaGraph(cfg, ssaBlocks, factory.size(), this.varCount, constants);
    }

    private void initBlocks(int maxStack) {
        List<BasicBlock> blocks = cfg.blocks();
        ssaBlocks = new ArrayList<>(blocks.size());
        ssaByBlockId = new SsaBlock[blocks.size()];
        for (BasicBlock block : blocks) {
            SsaBlock ssaBlock = block.newSsaBlock(maxStack, varCount);
            ssaBlocks.add(ssaBlock);
            ssaByBlockId[block.id()] = ssaBlock;
        }
    }

    private void initUndefinedVars() {
        undefinedVars = new SsaValue[varCount];
        for (int i = 0; i < varCount; i++) {
            undefinedVars[i] = factory.create(SsaKind.UNDEFINED, null, null, i, null);
        }
    }

    private void runWorkList() {
        SsaBlock entry = ssaBlocks.get(0);
        entry.inStack = StackState.empty();
        entry.inVars = new VarState(varCount);
        for (int i = 0; i < varCount; i++) {
            entry.inVars.set(i, undefinedVars[i]);
        }

        ArrayDeque<SsaBlock> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[ssaBlocks.size()];
        enqueue(entry, queue, queued);

        while (!queue.isEmpty()) {
            SsaBlock block = queue.poll();
            queued[block.id()] = false;
            transfer(block, queue, queued);
        }
    }

    private void transfer(SsaBlock block, ArrayDeque<SsaBlock> queue, boolean[] queued) {
        if (block.inStack == null || block.inVars == null) {
            return;
        }
        StackState stack = block.inStack.copy();
        VarState vars = block.inVars.copy();

        for (int i = 0; i < block.basicBlock().codeLength(); i++) {
            Bytecode bytecode = block.basicBlock().codeAt(i);
            if (bytecode.mayThrow() && bytecode.op() != Op.THROW_EXP) {
                mergeExceptionState(block, bytecode, vars, null, queue, queued);
            }
            execute(block, i, bytecode, stack, vars, queue, queued);
        }

        block.outStack = stack.copy();
        block.outVars = vars.copy();

        for (Edge edge : block.basicBlock().successors()) {
            if (edge.kind() == EdgeKind.EXCEPTION) {
                continue;
            }
            SsaBlock target = ssaByBlockId[edge.to().id()];
            if (mergeNormalState(target, stack, vars)) {
                enqueue(target, queue, queued);
            }
        }
    }

    private void execute(SsaBlock block,
                         int codeIndex,
                         Bytecode bytecode,
                         StackState stack,
                         VarState vars,
                         ArrayDeque<SsaBlock> queue,
                         boolean[] queued) {
        switch (bytecode.op()) {
            case NOOP:
                instruction(block, codeIndex, bytecode, SsaKind.NOOP, -1);
                break;
            case LOAD_CONST:
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.CONST, bytecode.operandIndex()));
                break;
            case LOAD_ROOT:
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.ROOT, -1));
                break;
            case DUMP: {
                SsaValue value = instruction(block, codeIndex, bytecode, SsaKind.DUP, -1, stack.peek());
                stack.push(value);
                break;
            }
            case POP: {
                SsaValue value = stack.pop(bytecode.pc());
                instruction(block, codeIndex, bytecode, SsaKind.POP, -1, value);
                break;
            }
            case LOAD_VAR: {
                SsaValue value = vars.get(bytecode.varIndex());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.LOAD_VAR, bytecode.varIndex(), value));
                break;
            }
            case STORE_VAR: {
                SsaValue value = stack.pop(bytecode.pc());
                SsaValue store = instruction(block, codeIndex, bytecode, SsaKind.STORE_VAR, bytecode.varIndex(), value);
                vars.set(bytecode.varIndex(), store);
                break;
            }
            case NEW_OBJECT:
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.NEW_OBJECT, -1));
                break;
            case NEW_ARRAY:
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.NEW_ARRAY, -1));
                break;
            case EXP_OBJECT:
                replaceParentAndAddition(block, codeIndex, bytecode, stack, SsaKind.EXP_OBJECT);
                break;
            case EXP_ARRAY:
                replaceParentAndAddition(block, codeIndex, bytecode, stack, SsaKind.EXP_ARRAY);
                break;
            case PUSH_ARRAY:
                replaceParentAndAddition(block, codeIndex, bytecode, stack, SsaKind.PUSH_ARRAY);
                break;
            case IDX_GET: {
                SsaValue key = stack.pop(bytecode.pc());
                SsaValue parent = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.INDEX_GET, -1, parent, key));
                break;
            }
            case IDX_SET:
            case IDX_SET_1: {
                SsaValue value = stack.pop(bytecode.pc());
                SsaValue key = stack.pop(bytecode.pc());
                SsaValue parent = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.INDEX_SET, -1, parent, key, value));
                break;
            }
            case PROP_GET: {
                SsaValue parent = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.PROP_GET, bytecode.operandIndex(), parent));
                break;
            }
            case PROP_SET:
            case PROP_SET_1: {
                SsaValue value = stack.pop(bytecode.pc());
                SsaValue parent = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.PROP_SET, bytecode.operandIndex(), parent, value));
                break;
            }
            case BOP_PLUS:
            case BOP_MINUS:
            case BOP_MULTIPLY:
            case BOP_DIVIDE:
            case BOP_MOD:
            case BOP_MATCH:
            case BOP_LT:
            case BOP_LTE:
            case BOP_GT:
            case BOP_GTE:
            case BOP_EQ:
            case BOP_SEQ:
            case BOP_NE:
            case BOP_SNE:
            case BOP_IN: {
                SsaValue right = stack.pop(bytecode.pc());
                SsaValue left = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.BINARY, -1, left, right));
                break;
            }
            case UNARY_PLUS:
            case UNARY_MINUS:
            case UNARY_NEG:
            case UNARY_TYPEOF: {
                SsaValue value = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.UNARY, -1, value));
                break;
            }
            case CALL_FUNC:
            case CALL_ASYNC_FUNC:
                call(block, codeIndex, bytecode, stack, bytecode.argCount(),
                        bytecode.isAsync() ? SsaKind.ASYNC_CALL : SsaKind.CALL);
                break;
            case CALL_FUNC_SPREAD:
            case CALL_ASYNC_FUNC_SPREAD: {
                SsaValue spread = stack.pop(bytecode.pc());
                stack.push(instruction(block, codeIndex, bytecode,
                        bytecode.isAsync() ? SsaKind.ASYNC_CALL : SsaKind.CALL,
                        bytecode.operandIndex(), spread));
                break;
            }
            case CALL_CONST:
            case CALL_ASYNC_CONST:
                stack.push(instruction(block, codeIndex, bytecode,
                        bytecode.isAsync() ? SsaKind.ASYNC_CONST_CALL : SsaKind.CONST_CALL,
                        bytecode.operandIndex()));
                break;
            case JUMP:
                instruction(block, codeIndex, bytecode, SsaKind.JUMP, bytecode.targetPc());
                break;
            case JUMP_IF_FALSE:
            case JUMP_IF_TRUE: {
                SsaValue value = stack.pop(bytecode.pc());
                instruction(block, codeIndex, bytecode, SsaKind.BRANCH, bytecode.targetPc(), value);
                break;
            }
            case ITERATE_INTO: {
                SsaValue value = stack.pop(bytecode.pc());
                SsaValue iterator = instruction(block, codeIndex, bytecode, SsaKind.ITERATE_INTO, bytecode.varIndex(), value);
                vars.set(bytecode.varIndex(), iterator);
                break;
            }
            case ITERATE_NEXT: {
                SsaValue iterator = vars.get(bytecode.varIndex());
                stack.push(instruction(block, codeIndex, bytecode, SsaKind.ITERATE_NEXT, bytecode.varIndex(), iterator));
                break;
            }
            case ITERATE_KEY:
            case ITERATE_VALUE: {
                SsaValue iterator = vars.get(bytecode.iteratorIndex());
                SsaValue value = instruction(block, codeIndex, bytecode, SsaKind.ITERATE_VAR, bytecode.storeIndex(), iterator);
                vars.set(bytecode.storeIndex(), value);
                break;
            }
            case INTO_CATCH: {
                SsaValue exception = block.exceptionValue;
                if (exception == null) {
                    exception = block.exceptionPhi(factory);
                    block.exceptionValue = exception;
                }
                SsaValue value = instruction(block, codeIndex, bytecode, SsaKind.ERROR_TO_OBJ, bytecode.varIndex(), exception);
                vars.set(bytecode.varIndex(), value);
                break;
            }
            case THROW_EXP: {
                SsaValue value = stack.pop(bytecode.pc());
                SsaValue thrown = instruction(block, codeIndex, bytecode, SsaKind.THROW, -1, value);
                mergeExceptionState(block, bytecode, vars, thrown, queue, queued);
                break;
            }
            case END_RETURN: {
                SsaValue value = stack.isEmpty() ? null : stack.peek();
                if (value == null) {
                    instruction(block, codeIndex, bytecode, SsaKind.RETURN, -1);
                } else {
                    instruction(block, codeIndex, bytecode, SsaKind.RETURN, -1, value);
                }
                break;
            }
            default:
                throw new SsaException("unsupported opcode in ssa builder: " + bytecode);
        }
    }

    private void replaceParentAndAddition(SsaBlock block,
                                          int codeIndex,
                                          Bytecode bytecode,
                                          StackState stack,
                                          SsaKind kind) {
        SsaValue addition = stack.pop(bytecode.pc());
        SsaValue parent = stack.pop(bytecode.pc());
        stack.push(instruction(block, codeIndex, bytecode, kind, -1, parent, addition));
    }

    private void call(SsaBlock block, int codeIndex, Bytecode bytecode, StackState stack, int argCount, SsaKind kind) {
        SsaValue[] args = new SsaValue[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = stack.pop(bytecode.pc());
        }
        stack.push(instruction(block, codeIndex, bytecode, kind, bytecode.operandIndex(), args));
    }

    private SsaValue instruction(SsaBlock block, int codeIndex, Bytecode bytecode, SsaKind kind, int index, SsaValue... operands) {
        return block.instruction(codeIndex, kind, bytecode, index, asList(operands), factory);
    }

    private static List<SsaValue> asList(SsaValue... operands) {
        if (operands == null || operands.length == 0) {
            return Collections.emptyList();
        }
        ArrayList<SsaValue> list = new ArrayList<>(operands.length);
        for (SsaValue operand : operands) {
            if (operand != null) {
                list.add(operand);
            }
        }
        return list;
    }

    private void mergeExceptionState(SsaBlock source,
                                     Bytecode bytecode,
                                     VarState vars,
                                     SsaValue exception,
                                     ArrayDeque<SsaBlock> queue,
                                     boolean[] queued) {
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            if (!region.containsTryPc(bytecode.pc())) {
                continue;
            }
            SsaBlock target = ssaByBlockId[region.catchBlock().id()];
            SsaValue exceptionValue = exception;
            if (exceptionValue == null) {
                exceptionValue = source.exceptionAt(bytecode.pc() - source.basicBlock().startPc(), bytecode, factory);
            }
            boolean changed = mergeExceptionInput(target, vars, exceptionValue);
            if (changed) {
                enqueue(target, queue, queued);
            }
        }
    }

    private boolean mergeNormalState(SsaBlock target, StackState stack, VarState vars) {
        boolean changed = false;
        if (target.inStack == null) {
            target.inStack = stack.copy();
            changed = true;
        } else {
            changed |= mergeStack(target, stack);
        }
        if (target.inVars == null) {
            target.inVars = vars.copy();
            changed = true;
        } else {
            changed |= mergeVars(target, vars);
        }
        return changed;
    }

    private boolean mergeExceptionInput(SsaBlock target, VarState vars, SsaValue exception) {
        boolean changed = false;
        if (target.inStack == null) {
            target.inStack = StackState.empty();
            changed = true;
        } else if (!target.inStack.isEmpty()) {
            throw new SsaException("catch block entry stack is not empty: " + target);
        }
        if (target.inVars == null) {
            target.inVars = vars.copy();
            changed = true;
        } else {
            changed |= mergeVars(target, vars);
        }
        if (target.exceptionValue == null) {
            target.exceptionValue = exception;
            changed = true;
        } else if (target.exceptionValue != exception) {
            SsaValue phi = target.exceptionValue.isPhi() ? target.exceptionValue : target.exceptionPhi(factory);
            if (phi != target.exceptionValue) {
                phi.addOperand(target.exceptionValue);
                target.exceptionValue = phi;
                changed = true;
            }
            changed |= phi.addOperand(exception);
        }
        return changed;
    }

    private boolean mergeStack(SsaBlock target, StackState incoming) {
        if (target.inStack.size() != incoming.size()) {
            throw new SsaException("stack height mismatch at block " + target.basicBlock()
                    + ": " + target.inStack.size() + " vs " + incoming.size());
        }
        boolean changed = false;
        SsaValue[] values = target.inStack.snapshot();
        for (int i = 0; i < values.length; i++) {
            SsaValue merged = mergeValue(target, values[i], incoming.get(i), i, true);
            if (merged != values[i]) {
                values[i] = merged;
                changed = true;
            }
        }
        if (changed) {
            StackState state = StackState.empty();
            for (SsaValue value : values) {
                state.push(value);
            }
            target.inStack = state;
        }
        return changed;
    }

    private boolean mergeVars(SsaBlock target, VarState incoming) {
        if (target.inVars.size() != incoming.size()) {
            throw new SsaException("var size mismatch at block " + target.basicBlock());
        }
        boolean changed = false;
        SsaValue[] values = target.inVars.snapshot();
        for (int i = 0; i < values.length; i++) {
            SsaValue merged = mergeValue(target, values[i], incoming.get(i), i, false);
            if (merged != values[i]) {
                values[i] = merged;
                changed = true;
            }
        }
        if (changed) {
            VarState state = new VarState(values.length);
            for (int i = 0; i < values.length; i++) {
                state.set(i, values[i]);
            }
            target.inVars = state;
        }
        return changed;
    }

    private SsaValue mergeValue(SsaBlock target, SsaValue current, SsaValue incoming, int index, boolean stack) {
        if (current == incoming) {
            return current;
        }
        SsaValue phi;
        if (current.isPhi()) {
            phi = current;
        } else {
            phi = stack ? target.stackPhi(index, factory) : target.varPhi(index, factory);
            phi.addOperand(current);
        }
        phi.addOperand(incoming);
        return phi;
    }

    private static void enqueue(SsaBlock block, ArrayDeque<SsaBlock> queue, boolean[] queued) {
        if (!queued[block.id()]) {
            queue.add(block);
            queued[block.id()] = true;
        }
    }

    private static int deriveVarCount(Cfg cfg) {
        int max = -1;
        for (Bytecode bytecode : cfg.bytecodes()) {
            if (bytecode.hasVarIndex()) {
                max = Math.max(max, bytecode.varIndex());
            }
            if (bytecode.hasIteratorIndex()) {
                max = Math.max(max, bytecode.iteratorIndex());
            }
            if (bytecode.hasStoreIndex()) {
                max = Math.max(max, bytecode.storeIndex());
            }
        }
        return max + 1;
    }
}
