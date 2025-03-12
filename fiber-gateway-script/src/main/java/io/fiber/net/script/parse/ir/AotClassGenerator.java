package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;
import io.fiber.net.script.run.InterpreterVm;

import java.util.*;

public class AotClassGenerator {
    private final TreeMap<Integer, CodeEnterPoint> jumpPc = new TreeMap<>();
    private final TreeMap<Integer, ExpTableItem> expTableMap = new TreeMap<>();


    private final Compiled compiled;
    private final ClzAssembler clzAssembler;
    private byte[] clzData;

    public AotClassGenerator(Compiled compiled) {
        this.compiled = compiled;
        this.clzAssembler = new ClzAssembler(compiled);
    }

    public String getGeneratedClzName() {
        return clzAssembler.getInternalClzName();
    }

    private void addPoint(int idx) {
        CodeEnterPoint point = jumpPc.get(idx);
        if (point != null) {
            return;
        }

        Assert.isTrue(idx >= 0 && idx < compiled.getCodes().length, "target pc out of codes???");
        jumpPc.put(idx, new CodeEnterPoint(idx, compiled.getStackSize(), compiled.getVarTableSize()));

    }

    public CodeEnterPoint getPoint(int idx) {
        return jumpPc.get(idx);
    }

    private void computePoint() {
        int[] codes = compiled.getCodes();
        int[] expIns = compiled.getExpIns();
        if (expIns != null) {
            for (int i = 0; i < expIns.length; i++) {
                int codeIdx = expIns[i];
                if (codeIdx < 0) {
                    continue;
                }
                addPoint(codeIdx);
                if (i >= (expIns.length >> 1)) {
                    // exception point
                    CodeEnterPoint point = getPoint(codeIdx);
                    point.setWriteFrame(true);
                    point.setCatchPoint();
                }
            }
            int[] exceptionTable = compiled.getExceptionTable();
            for (int i = 0; i < exceptionTable.length; i += 3) {
                int codeIdx = exceptionTable[i + 2];
                if (codeIdx < codes.length) {
                    addPoint(codeIdx);
                }
            }
        }

        addPoint(0);
        for (int i = 0; i < codes.length; i++) {
            int code = codes[i];
            switch (code & 0xFF) {
                case Code.CALL_ASYNC_FUNC:
                case Code.CALL_ASYNC_FUNC_SPREAD:
                case Code.CALL_ASYNC_CONST:
//                    asyncPc.put(i, new CodeEnterPoint(i, compiled.stackSize));
                    break;
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:
                    addPoint(i + 1);
                case Code.JUMP: {
                    int target = code >>> 8;
                    addPoint(target);
                    getPoint(target).setWriteFrame(true);
                    break;
                }
                case Code.INTO_CATCH:
                    Assert.isTrue(getPoint(i) != null);
                default:
                    break;
            }
        }

        CodeEnterPoint currentPoint = null;
        for (int i = 0; i < codes.length; i++) {
            CodeEnterPoint cp = getPoint(i);
            if (cp != null) {
                currentPoint = cp;
            }
            assert currentPoint != null;
            int code = codes[i];
            switch (code & 0xFF) {
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:
                    currentPoint.addNextPoint(getPoint(i + 1));
                    getPoint(code >>> 8);
                case Code.JUMP:
                    currentPoint.addNextPoint(getPoint(code >>> 8));
                    break;
                default:
                    break;
            }
        }

        if (expIns != null) {
            // try 之前的 entry 和 try {} 中间的 try 都是 catch 的直接前
            int[] exceptionTable = compiled.getExceptionTable();
            for (int i = 0; i < exceptionTable.length; i += 3) {
                int tryBegin = exceptionTable[i];
                int catchBegin = exceptionTable[i + 1];
                CodeEnterPoint catchPoint = getPoint(catchBegin);
                if (tryBegin > 0) {
                    jumpPc.lowerEntry(tryBegin).getValue().addNextPoint(catchPoint);
                }
                CodeEnterPoint tryBeginPoint = getPoint(tryBegin);
                tryBeginPoint.addNextPoint(catchPoint);
                ExpTableItem tableItem = new ExpTableItem(catchPoint, tryBeginPoint.getStartLabel());
                expTableMap.put(catchBegin, tableItem);
                tableItem.setTryEndLabel(tryBeginPoint.getEndLabel());

                int tb = tryBegin;
                Map.Entry<Integer, CodeEnterPoint> entry;
                while ((entry = jumpPc.higherEntry(tb)) != null && (tb = entry.getKey()) < catchBegin) {
                    entry.getValue().addNextPoint(catchPoint);
                    tableItem.setTryEndLabel(entry.getValue().getEndLabel());
                }
            }
        }

        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            CodeEnterPoint ep = entry.getValue();
            if (ep.getCodeIdx() > 0) {
                switch (codes[ep.getCodeIdx() - 1] & 0xFF) {
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE:
                    case Code.JUMP:
                    case Code.THROW_EXP:
                    case Code.END_RETURN:
                        break;
                    default:
                        jumpPc.floorEntry(ep.getCodeIdx() - 1).getValue().addNextPoint(ep);
                        break;
                }
            }
        }
    }

    private void computeConnection() {
        int[] codes = compiled.getCodes();
        Iterator<Map.Entry<Integer, CodeEnterPoint>> iterator = jumpPc.entrySet().iterator();
        Map.Entry<Integer, CodeEnterPoint> prev = iterator.next();
        prev.getValue().sortEntries();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CodeEnterPoint> current = iterator.next();
            prev.getValue().setCodeLen(current.getKey() - prev.getKey());
            prev = current;
            prev.getValue().sortEntries();
        }
        prev.getValue().setCodeLen(codes.length - prev.getKey());
    }

    private void convertIREnterPoint(CodeEnterPoint ep) {
        int[] codes = compiled.getCodes();

        Assert.isTrue(ep != null && !ep.isInstrumentsFilled());
        CodeEnterPoint next;
        for (int i = ep.getCodeIdx(); i < codes.length; i++) {
            if ((next = getPoint(i)) != null && next != ep) {
                return;
            }

            int code = codes[i];
            int ins = code & 0xFF;
            switch (ins) {
                case Code.NOOP:
                    ep.noop();
                    break;
                case Code.LOAD_CONST:
                    ep.loadConst(clzAssembler.addLiteralExtOperand(code >>> 8));
                    break;
                case Code.LOAD_ROOT:
                    ep.loadRoot();
                    break;
                case Code.DUMP:
                    ep.dump();
                    break;
                case Code.POP:
                    ep.pop();
                    break;
                case Code.LOAD_VAR:
                    ep.loadVar(code >>> 8);
                    break;
                case Code.STORE_VAR:
                    ep.storeVar(code >>> 8);
                    break;
                case Code.NEW_OBJECT:
                    ep.newObject();
                    break;
                case Code.NEW_ARRAY:
                    ep.newArray();
                    break;
                case Code.EXP_OBJECT:
                    ep.expObject();
                    break;
                case Code.EXP_ARRAY:
                    ep.expArray();
                    break;
                case Code.PUSH_ARRAY:
                    ep.pushArray();
                    break;
                //
                case Code.IDX_SET://!
                    ep.idxSet();
                    break;
                case Code.IDX_GET://!
                    ep.idxGet();
                    break;
                case Code.PROP_GET://!
                    ep.propGet(clzAssembler.addStringProp(code >>> 8));
                    break;
                case Code.PROP_SET://! {a:1}
                    ep.propSet(clzAssembler.addStringProp(code >>> 8));
                    break;
                case Code.PROP_SET_1://! {a:1}
                    ep.propSet1(clzAssembler.addStringProp(code >>> 8));
                    break;
                //
                case Code.BOP_PLUS:
                case Code.BOP_MINUS:
                case Code.BOP_MULTIPLY:
                case Code.BOP_DIVIDE:
                case Code.BOP_MOD:
                case Code.BOP_MATCH: //~

                case Code.BOP_LT: //<
                case Code.BOP_LTE: //<=
                case Code.BOP_GT: //~
                case Code.BOP_GTE: //~
                case Code.BOP_EQ: //~
                case Code.BOP_SEQ: //~
                case Code.BOP_NE: //~
                case Code.BOP_SNE: //~
                case Code.BOP_IN: // in
                    ep.binaryOperator(ins);
                    break;
                //
                case Code.UNARY_PLUS:
                case Code.UNARY_MINUS:
                case Code.UNARY_NEG://!
                case Code.UNARY_TYPEOF://!
                    ep.unaryOperator(ins);
                    break;
                case Code.CALL_FUNC: {
                    ep.function(clzAssembler.addFunction(code >>> 16), (code >>> 8) & 0xFF, false);
                    break;
                }
                case Code.CALL_FUNC_SPREAD: {
                    ep.function(clzAssembler.addFunction(code >>> 8), 1, true);
                    break;
                }

                case Code.CALL_ASYNC_FUNC: {
                    ep.asyncFunction(clzAssembler.addAsyncFunction(code >>> 16), clzAssembler.addAsyncPoint(), (code >>> 8) & 0xFF, false);
                    break;
                }
                case Code.CALL_ASYNC_FUNC_SPREAD: {
                    ep.asyncFunction(clzAssembler.addAsyncFunction(code >>> 16), clzAssembler.addAsyncPoint(), (code >>> 8) & 0xFF, true);
                    break;
                }
                case Code.CALL_CONST:
                    ep.constant(clzAssembler.addConstant(code >>> 8), -1);
                    break;
                case Code.CALL_ASYNC_CONST:
                    ep.constant(clzAssembler.addAsyncConstant(code >>> 8), clzAssembler.addAsyncPoint());
                    break;
                case Code.JUMP:
                    ep.jump(jumpPc.get(code >>> 8));
                    break;
                case Code.JUMP_IF_FALSE:
                    ep.conditionalJump(false, jumpPc.get(code >>> 8));
                    break;
                case Code.JUMP_IF_TRUE:
                    ep.conditionalJump(true, jumpPc.get(code >>> 8));
                    break;
                case Code.ITERATE_INTO:
                    ep.iterateInto(code >>> 8);
                    break;
                case Code.ITERATE_NEXT:
                    ep.iterateNext(code >>> 8);
                    break;
                case Code.ITERATE_KEY:
                    ep.iterateVar(code >>> InterpreterVm.ITERATOR_OFF,
                            (code >>> InterpreterVm.INSTRUMENT_LEN) & InterpreterVm.MAX_ITERATOR_VAR, true);
                    break;
                case Code.ITERATE_VALUE:
                    ep.iterateVar(code >>> InterpreterVm.ITERATOR_OFF,
                            (code >>> InterpreterVm.INSTRUMENT_LEN) & InterpreterVm.MAX_ITERATOR_VAR, false);
                    break;

                case Code.INTO_CATCH:
                    ep.intoCatch(code >>> InterpreterVm.INSTRUMENT_LEN);
                    break;
                case Code.THROW_EXP:
                    ep.throwExp();
                    break;
                case Code.END_RETURN:
                    ep.ret();
                    break;
                default:
                    throw new IllegalStateException("unknown code:" + code);
            }
        }

        if (ep.getCodeIdx() == compiled.getCodes().length) {
            // return void
            ep.vRet();
        }
    }

    public Class<?> generateClz() throws Throwable {
        return loadAsClz();
    }

    public String getClzName() {
        return clzAssembler.getInternalClzName();
    }

    /**
     * for test;
     *
     * @return data for class file
     */
    public byte[] generateClzData() {
        if (clzData != null) {
            return clzData;
        }
        convertAndPreGenerate();
        return clzData = clzAssembler.asmClzData();
    }

    public Class<?> loadAsClz() throws Throwable {
        return clzAssembler.loadAsClz(generateClzData());
    }

    private void convertAndPreGenerate() {
        computePoint();
        computeConnection();
        convertIR();

        clzAssembler.setExceptionTable(expTableMap);
        clzAssembler.preAms();
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            entry.getValue().asmCodes(clzAssembler);
        }
    }

    private void convertIR() {
        convertIREnterPointTree(getPoint(0));

        int maxStashStack = 0;
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            CodeEnterPoint enterPoint = entry.getValue();
            Assert.isTrue(enterPoint.isInstrumentsFilled());
            maxStashStack = Integer.max(enterPoint.getStackStashSize(), maxStashStack);
        }
        clzAssembler.setMaxStashStack(maxStashStack);

        if (compiled.getVarTableSize() == 0) {
            return;
        }
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            entry.getValue().dealVarUse();
        }

        HashSet<CodeEnterPoint> visited = new LinkedHashSet<>();
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            entry.getValue().fixAsyncVar(visited);
        }

        int maxSyncSize = -1;
        int maxAsyncSize = -1;
        VarTable prev = null;
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            CodeEnterPoint value = entry.getValue();
            VarTable varTable = value.computeVarForFrame();
            maxSyncSize = Math.max(maxSyncSize, varTable.getMaxSyncSize());
            maxAsyncSize = Math.max(maxAsyncSize, varTable.getMaxAsyncSize());
            if (value.isWriteFrame()) {
                varTable.setPrevTableForFrame(prev);
                prev = varTable;
            }
        }
        clzAssembler.setMaxLocalVarTableSize(maxSyncSize);
        clzAssembler.setMaxAsyncVarTableSize(maxAsyncSize);
    }

    private void convertIREnterPointTree(CodeEnterPoint point) {
        Queue<CodeEnterPoint> points = new ArrayDeque<>();
        point.setEmptyInitStack();
        points.add(point);
        do {
            point = points.poll();
            assert point != null;
            if (point.isInstrumentsFilled()) {
                continue;
            }
            convertIREnterPoint(point);
            Assert.isTrue(point.isInstrumentsFilled());
            point.copyStackToNext();

            List<CodeEnterPoint> nextPoints = point.getNextPoints();
            if (CollectionUtils.isNotEmpty(nextPoints)) {
                for (CodeEnterPoint nextPoint : nextPoints) {
                    if (nextPoint.isInstrumentsFilled()) {
                        continue;
                    }
                    points.add(nextPoint);
                }
            }
        } while (!points.isEmpty());
    }

}
