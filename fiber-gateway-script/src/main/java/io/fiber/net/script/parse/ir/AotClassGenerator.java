package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;
import io.fiber.net.script.run.Vm;

import java.util.*;

public class AotClassGenerator {


    private static class ExpTableItem {
        private final List<CodeEnterPoint> tryRange = new ArrayList<>();
        private final CodeEnterPoint catchPoint;
        private final int tryEndIdx;

        private ExpTableItem(CodeEnterPoint catchPoint, int tryEndIdx) {
            this.catchPoint = catchPoint;
            this.tryEndIdx = tryEndIdx;
        }

        public void add(CodeEnterPoint tryPoint) {
            tryRange.add(tryPoint);
        }
    }

    private final TreeMap<Integer, CodeEnterPoint> jumpPc = new TreeMap<>();
    private final TreeMap<Integer, ExpTableItem> expTableMap = new TreeMap<>();
    private final TreeMap<Integer, CodeEnterPoint> tryExpTableMap = new TreeMap<>();


    private final Compiled compiled;
    private final ClzAssembler clzAssembler;

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
        if (idx < compiled.getCodes().length) {
            jumpPc.put(idx, new CodeEnterPoint(idx, compiled.getStackSize()));
        } else {
            Assert.isTrue(compiled.getCodes().length == idx);
            jumpPc.put(idx, new CodeEnterPoint(idx, 0));
        }
    }

    public CodeEnterPoint getPoint(int idx) {
        return jumpPc.get(idx);
    }

    private void computePoint() {
        int[] exceptionTable = compiled.getExceptionTable();
        if (exceptionTable != null) {
            Stack<ExpTableItem> expStack = new Stack<>();
            for (int i = 0; i < exceptionTable.length; i += 3) {
                int tryStartIdx = exceptionTable[i];
                int tryEndIdx = exceptionTable[i + 1];
                int catchIdx = exceptionTable[i + 2];
                addPoint(tryStartIdx);
                addPoint(catchIdx);
                while (!expStack.empty() && tryStartIdx >= expStack.peek().tryEndIdx) {
                    ExpTableItem pop = expStack.pop();
                    Assert.isTrue(tryEndIdx < pop.tryEndIdx);
                }
                for (ExpTableItem expTableItem : expStack) {
                    expTableItem.add(getPoint(tryStartIdx));
                }
                ExpTableItem item = new ExpTableItem(getPoint(catchIdx), tryEndIdx);
                item.add(getPoint(tryStartIdx));
                expTableMap.put(catchIdx, item);
                tryExpTableMap.put(tryEndIdx, getPoint(tryStartIdx));
            }
            Assert.isTrue(expStack.empty());
        }

        int[] codes = compiled.getCodes();
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
                case Code.JUMP:
                    addPoint(code >>> 8);
                    break;
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
                case Code.JUMP:
                    currentPoint.addNextPoint(getPoint(code >>> 8));
                    break;
                case Code.ITERATE_INTO:
                    currentPoint.addNextPoint(getPoint(i + 1));
                    break;
                case Code.INTO_CATCH: {
                    CodeEnterPoint tryStart = expTableMap.get(i).tryRange.get(0);
                    Map.Entry<Integer, CodeEnterPoint> entry = jumpPc.lowerEntry(tryStart.getCodeIdx());
                    if (entry != null) {
                        entry.getValue().addNextPoint(tryStart);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void computeConnection() {
        int[] codes = compiled.getCodes();
        CodeEnterPoint ep = null;
        for (int i = 0; i < codes.length; i++) {
            if (jumpPc.containsKey(i)) {
                ep = jumpPc.get(i);
            }
            assert ep != null;
            int code = codes[i];
            switch (code & 0xFF) {
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:
                case Code.JUMP:
                    break;
                default:
                    break;
            }
        }
        assert !jumpPc.isEmpty();
        Iterator<Map.Entry<Integer, CodeEnterPoint>> iterator = jumpPc.entrySet().iterator();
        Map.Entry<Integer, CodeEnterPoint> prev = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CodeEnterPoint> current = iterator.next();
            prev.getValue().setCodeLen(current.getKey() - prev.getKey());
            prev = current;
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
                    ep.function(clzAssembler.addFunction(code >>> 16), (code >>> 8) & 0xFF, true);
                    break;
                }

                case Code.CALL_ASYNC_FUNC: {
                    ep.asyncFunction(clzAssembler.addAsyncFunction(code >>> 16), (code >>> 8) & 0xFF, false);
                    break;
                }
                case Code.CALL_ASYNC_FUNC_SPREAD: {
                    ep.asyncFunction(clzAssembler.addAsyncFunction(code >>> 16), (code >>> 8) & 0xFF, true);
                    break;
                }
                case Code.CALL_CONST:
                    ep.constant(clzAssembler.addConstant(code >>> 8), false);
                    break;
                case Code.CALL_ASYNC_CONST:
                    ep.constant(clzAssembler.addAsyncConstant(code >>> 8), true);
                    break;
                case Code.JUMP:
                    ep.jump(jumpPc.get(code >>> 8));
                    break;
                case Code.JUMP_IF_FALSE:
                    ep.conditionalJump(jumpPc.get(i + 1), jumpPc.get(code >>> 8));
                    break;
                case Code.JUMP_IF_TRUE:
                    ep.conditionalJump(jumpPc.get(code >>> 8), jumpPc.get(i + 1));
                    break;
                case Code.ITERATE_INTO:
                    ep.iterateInto(code >>> 8);
                    break;
                case Code.ITERATE_NEXT:
                    ep.iterateNext(code >>> 8);
                    break;
                case Code.ITERATE_KEY:
                    ep.iterateVar(code >>> Vm.ITERATOR_OFF,
                            (code >>> Vm.INSTRUMENT_LEN) & Vm.MAX_ITERATOR_VAR, true);
                    break;
                case Code.ITERATE_VALUE:
                    ep.iterateVar(code >>> Vm.ITERATOR_OFF,
                            (code >>> Vm.INSTRUMENT_LEN) & Vm.MAX_ITERATOR_VAR, false);
                    break;

                case Code.INTO_CATCH:
                    ep.intoCatch(code >>> Vm.INSTRUMENT_LEN);
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
        convertAndPreGenerate();
        return clzAssembler.asmClz();
    }

    /**
     * for test;
     *
     * @return data for class file
     * @throws Exception exp
     */
    public byte[] generateClzData() throws Throwable {
        convertAndPreGenerate();
        return clzAssembler.asmClzData();
    }

    private void convertAndPreGenerate() {
        computePoint();
        computeConnection();
        convertIR();

        clzAssembler.preAms();
        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            CodeEnterPoint value = entry.getValue();
            int sp = value.getInitStackSize();
            for (Instrument code : value.getCodes()) {
                sp += code.assemble(clzAssembler);
            }
            if (CollectionUtils.isEmpty(value.getNextPoints())) {
                Assert.isTrue(sp == 0);
            } else {
                for (CodeEnterPoint nextPoint : value.getNextPoints()) {
                    Assert.isTrue(sp == nextPoint.getInitStackSize());
                }
            }
        }
    }

    private void convertIR() {
        convertIREnterPointTree(getPoint(0));

        for (Map.Entry<Integer, CodeEnterPoint> entry : jumpPc.entrySet()) {
            CodeEnterPoint enterPoint = entry.getValue();
            if (expTableMap.containsKey(entry.getKey())) {
                // catchPoint
                convertIREnterPointTree(enterPoint);
            }
            Assert.isTrue(enterPoint.isInstrumentsFilled());
        }
    }

    private void convertIREnterPointTree(CodeEnterPoint point) {
        Queue<CodeEnterPoint> points = new ArrayDeque<>();
        point.setEmptyInitStack();
        points.add(point);
        do {
            point = points.poll();
            assert point != null;
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
