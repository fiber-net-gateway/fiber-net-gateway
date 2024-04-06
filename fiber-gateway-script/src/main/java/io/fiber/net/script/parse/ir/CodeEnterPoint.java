package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class CodeEnterPoint {
    private static final Exp[] EMPTY_EXP = new Exp[0];
    private final int codeIdx;
    private final Exp[] ins;
    private int sp;
    private StackRef[] initStackIns;
    private boolean hasInitStackIns;
    private Instrument[] codes;
    private int cl;


    private List<CodeEnterPoint> nextPoints;

    public CodeEnterPoint(int codeIdx, int stack) {
        this.codeIdx = codeIdx;
        ins = stack > 0 ? new Exp[stack] : EMPTY_EXP;
    }

    public void setCodeLen(int len) {
        codes = new Instrument[Integer.max(len, 1)];
    }

    private void addCode(Instrument instrument) {
        codes[cl++] = instrument;
    }

    public int getCodeIdx() {
        return codeIdx;
    }

    void setInitStack(Exp[] init) {
        if (hasInitStackIns) {
            if (ArrayUtils.isEmpty(init)) {
                Assert.isTrue(initStackIns == null);
            } else {
                Assert.isTrue(initStackIns != null && initStackIns.length == init.length);
                for (int i = 0; i < init.length; i++) {
                    initStackIns[i].add(init[i]);
                }
            }
        } else {
            Assert.isTrue(initStackIns == null && sp == 0);
            Assert.isTrue(!isInstrumentsFilled());
            hasInitStackIns = true;

            if (ArrayUtils.isEmpty(init)) {
                return;
            }
            initStackIns = new StackRef[init.length];
            for (int i = 0; i < init.length; i++) {
                ins[i] = initStackIns[i] = StackRef.of(init[i]);
            }
            sp = init.length;
        }
    }

    public void setEmptyInitStack() {
        setInitStack(null);
    }

    public int getInitStackSize() {
        return initStackIns == null ? 0 : initStackIns.length;
    }

    public void addNextPoint(CodeEnterPoint next) {
        Assert.isTrue(next != null);
        if (nextPoints == null) {
            nextPoints = new ArrayList<>();
        } else {
            for (CodeEnterPoint nextPoint : nextPoints) {
                if (nextPoint == next) {
                    return;
                }
            }
        }
        nextPoints.add(next);
    }

    public void copyStackToNext() {
        Assert.isTrue(isInstrumentsFilled());

        if (nextPoints == null) {
            return;
        }
        if (sp == 0) {
            for (CodeEnterPoint nextPoint : nextPoints) {
                nextPoint.setEmptyInitStack();
            }
            return;
        }
        Exp[] exps = Arrays.copyOf(ins, sp);
        for (CodeEnterPoint nextPoint : nextPoints) {
            nextPoint.setInitStack(exps);
        }
    }

    public List<CodeEnterPoint> getNextPoints() {
        if (nextPoints == null) {
            return Collections.emptyList();
        }
        return nextPoints;
    }

    public boolean isInstrumentsFilled() {
        return cl > 0;
    }

    public void loadConst(int constValIdx) {
        LoadConst loadConst = LoadConst.of(constValIdx);
        ins[sp++] = loadConst;
        addCode(loadConst);
    }

    public void loadRoot() {
        LoadRoot loadRoot = LoadRoot.of();
        ins[sp++] = loadRoot;
        addCode(loadRoot);
    }

    public void noop() {
        addCode(Noop.INSTANCE);
    }

    public void dump() {
        Dump dump = Dump.of(ins[sp - 1]);
        ins[sp++] = dump;
        addCode(dump);
    }

    public void pop() {
        Pop pop = Pop.of();
        ins[--sp] = null;
        addCode(pop);
    }

    public void loadVar(int varIdx) {
        LoadVar var = LoadVar.of(varIdx);
        ins[sp++] = var;
        addCode(var);
    }

    public void storeVar(int varIdx) {
        StoreVar var = StoreVar.of(varIdx, ins[--sp]);
        ins[sp] = null;
        addCode(var);
    }

    public void newObject() {
        NewObjArray instrument = NewObjArray.of(true);
        ins[sp++] = instrument;
        addCode(instrument);
    }

    public void newArray() {
        NewObjArray instrument = NewObjArray.of(false);
        ins[sp++] = instrument;
        addCode(instrument);
    }

    public void expObject() {

        ExpObjArray instrument = ExpObjArray.of(true, ins[--sp]);
        addCode(instrument);
    }

    public void expArray() {
        ExpObjArray instrument = ExpObjArray.of(false, ins[--sp]);
        addCode(instrument);
    }

    public void pushArray() {
        addCode(PushArray.of(ins[--sp]));
    }

    //
    public void idxSet() {
        sp -= 2;
        IndexSet indexSet = IndexSet.of(ins[sp - 1], ins[sp], ins[sp + 1]);
        ins[sp - 1] = indexSet;
        addCode(indexSet);
    }//!

    public void idxGet() {
        sp -= 1;
        IndexGet get = IndexGet.of(ins[sp - 1], ins[sp]);
        ins[sp - 1] = get;
        addCode(get);
    }//!

    public void propGet(int keyId) {
        PropGet propGet = PropGet.of(ins[sp - 1], keyId);
        ins[sp - 1] = propGet;
        addCode(propGet);
    }

    public void propSet(int keyId) {
        sp -= 1;
        PropSet propSet = PropSet.of(ins[sp - 1], keyId, ins[sp]);
        ins[sp - 1] = propSet;
        addCode(propSet);
    }

    public void propSet1(int keyId) {
        sp -= 1;
        PropSet_1 propSet_1 = PropSet_1.of(ins[sp - 1], keyId, ins[sp]);
        ins[sp - 1] = propSet_1;
        addCode(propSet_1);
    }

    public void binaryOperator(int code) {
        int sp = this.sp;
        sp -= 2;
        Assert.isTrue(sp >= 0);
        Binary binary = Binary.of(code, ins[sp], ins[sp + 1]);
        ins[sp] = binary;
        ins[++sp] = null;
        this.sp = sp;
        addCode(binary);
    }

    public void unaryOperator(int code) {
        int sp = this.sp;
        Assert.isTrue(sp >= 1);
        addCode(ins[sp - 1] = Unary.of(code, ins[sp - 1]));
    }

    private void function0(int funcId, int argCount, boolean spread, boolean async) {
        int sp = this.sp;
        if (spread) {
            Assert.isTrue(argCount == 1);
            Assert.isTrue(sp >= 1);
            addCode(ins[sp - 1] = FunctionCall.spread(funcId, ins[sp - 1], async));
        } else {
            sp -= argCount;
            Assert.isTrue(sp >= 0);
            addCode(ins[sp] = FunctionCall.of(funcId, Arrays.copyOfRange(ins, sp, sp + argCount), async));
            this.sp = sp + 1;
        }
    }

    void function(int funcId, int argCount, boolean spread) {
        function0(funcId, argCount, spread, false);
    }


    public void asyncFunction(int asyncFuncId, int argCount, boolean spread) {
        function0(asyncFuncId, argCount, spread, true);
    }

    public void constant(int constId, boolean async) {
        Assert.isTrue(sp >= 0);
        addCode(ins[sp++] = ConstCall.of(constId, async));
    }

    public void conditionalJump(CodeEnterPoint truePoint, CodeEnterPoint falsePoint) {
        addCode(ConditionalJump.of(ins[--sp], truePoint, falsePoint));
    }

    public void jump(CodeEnterPoint target) {
        addCode(Jump.of(target));
    }

    public void iterateInto(int varIdx) {
        addCode(IterateInto.of(ins[--sp], varIdx));
    }

    public void iterateNext(int varIdx) {
        addCode(ins[sp++] = IterateNext.of(varIdx));
    }

    public void iterateVar(int iteratorIdx, int storeIdx, boolean key) {
        addCode(IterateVar.of(iteratorIdx, storeIdx, key));
    }

    public void throwExp() {
        addCode(Throw.of(ins[--sp]));
    }

    public void intoCatch(int expIdx) {
        addCode(IntoCatch.of(expIdx));
    }

    public void ret() {
        if (sp > 0) {
            addCode(Return.of(ins[--sp]));
        } else {
            vRet();
        }
    }

    public void vRet() {
        addCode(ReturnV.of());
    }

    Instrument[] getCodes() {
        return codes;
    }
}