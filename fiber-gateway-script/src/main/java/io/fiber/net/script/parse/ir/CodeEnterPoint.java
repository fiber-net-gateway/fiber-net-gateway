package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import org.objectweb.asm.Label;

import java.util.*;


public class CodeEnterPoint {
    private static final Exp[] EMPTY_EXP = new Exp[0];
    private final int codeIdx;
    private final Exp[] ins;
    private int sp;
    private StackRef[] initStackIns;
    private boolean hasInitStackIns;
    private Instrument[] codes;
    private int cl;
    private final Label startLabel = new Label();
    private final Label endLabel = new Label();
    private int stackStashSize;
    private int varStage;

    private final VarTable varTable;
    private boolean writeFrame;
    private boolean catchPoint;


    private List<CodeEnterPoint> nextPoints;
    private List<CodeEnterPoint> prevPoints;
    private List<Instrument> useVarInstruments;

    public CodeEnterPoint(int codeIdx, int stack, int varTableSize) {
        this.codeIdx = codeIdx;
        ins = stack > 0 ? new Exp[stack] : EMPTY_EXP;
        varTable = VarTable.getInstance(varTableSize);
    }

    public void setWriteFrame(boolean writeFrame) {
        this.writeFrame = writeFrame;
    }

    void setCatchPoint() {
        this.catchPoint = true;
    }

    public boolean isWriteFrame() {
        return writeFrame;
    }

    public void setCodeLen(int len) {
        codes = new Instrument[len];
    }

    private void addCode(Instrument instrument) {
        codes[cl++] = instrument;
        if (instrument instanceof VarLoad || instrument instanceof VarStore) {
            if (useVarInstruments == null) {
                useVarInstruments = new ArrayList<>();
            }
            useVarInstruments.add(instrument);
            if (instrument instanceof VarLoad) {
                VarLoad varLoad = (VarLoad) instrument;
                varLoad.setLoadVarStage(varStage);
                varLoad.setCodeIdx(cl - 1);
            }
            if (instrument instanceof VarStore) {
                VarStore varStore = (VarStore) instrument;
                varStore.setStoreVarStage(varStage);
                varStore.setCodeIdx(cl - 1);
                varStore.setCodeEnterPoint(this);
            }
        }
    }

    public void dealVarUse() {
        Assert.isTrue(isInstrumentsFilled());
        if (useVarInstruments != null) {
            for (Instrument instrument : useVarInstruments) {
                if (instrument instanceof VarStore) {
                    findOrDefVarAndStore((VarStore) instrument);
                }
                if (instrument instanceof VarLoad) {
                    VarLoad varLoad = (VarLoad) instrument;
                    VarTable.VarDef prevDef = findPrevDef(varLoad.getLoadIdx());
                    Assert.isTrue(prevDef != null, "use uninitialized var???");
                    varLoad.setLoadVar(prevDef);
                }
            }
        }

        int tableCapacity = varTable.getTableCapacity();
        int i;
        for (i = 0; i < tableCapacity; i++) {
            if (varTable.getDef(i) == null) {
                VarTable.VarDef def = findPrevDef(i);
                if (def == null) {
                    break;
                }
                varTable.setDef(def);
            }
        }

        while (i < tableCapacity) {
            Assert.isTrue(varTable.getDef(i) == null, "absent var ???");
            i++;
        }

    }

    private void findOrDefVarAndStore(VarStore varStore) {
        VarTable.VarDef def = findPrevDef(varStore.getStoreIdx());
        if (def != null) {
            varTable.addStore(def, varStore);
        } else {
            varTable.defAndWrite(varStore);
        }
    }

    private VarTable.VarDef findPrevDef(int varIdx) {
        VarTable.VarDef def = varTable.getDef(varIdx);
        if (def != null) {
            return def;
        }

        if (prevPoints == null) {
            return null;
        }

        VarTable.VarDef ld = null;
        for (CodeEnterPoint prevPoint : prevPoints) {
            if (getCodeIdx() <= prevPoint.getCodeIdx()) {
                continue;
            }
            def = prevPoint.findPrevDef(varIdx);
            if (def == null) {
                return null;
            }
            if (ld != null && def != ld) {
                return null;
            }
            ld = def;
        }
        if (def != null) {
            varTable.setDef(def);
        }
        return ld;
    }


    public void fixAsyncVar(HashSet<CodeEnterPoint> visited) {
        if (useVarInstruments == null) {
            return;
        }

        for (Instrument instrument : useVarInstruments) {
            if (instrument instanceof VarLoad) {
                VarLoad varLoad = (VarLoad) instrument;
                VarTable.VarDef def = varLoad.getLoadVar();
                if (!def.isAsync()) {
                    markAsyncRead(varLoad, visited);
                }
            }
        }
    }

    private void markAsyncRead(VarLoad varLoad, HashSet<CodeEnterPoint> visited) {
        int loadVarStage = varLoad.getLoadVarStage();
        int idx = varLoad.getCodeIdx();
        VarTable.VarDef def = varLoad.getLoadVar();

        VarStore store = varTable.getStore(def.getIdx());
        while (store != null) {
            Assert.isTrue(store.getStoreVar() == def);
            if (store.getCodeIdx() < idx) {
                break;
            }
            store = store.getPrevStore();
        }

        if (store != null) {
            Assert.isTrue(store.getStoreVar() == def);
            if (store.getStoreVarStage() == loadVarStage) {
                return;
            }
            Assert.isTrue(store.getStoreVarStage() < loadVarStage);
            def.setAsync();
            return;
        }

        if (loadVarStage > 0) {
            def.setAsync();
            return;
        }

        if (markAsyncAccordingPrevStore(def, visited)) {
            def.setAsync();
        }
    }

    private boolean markAsyncAccordingPrevStore(VarTable.VarDef def, HashSet<CodeEnterPoint> visited) {
        Assert.isTrue(prevPoints != null, "read uninitialized var???");
        boolean result = false;
        int idx = def.getIdx();
        for (CodeEnterPoint prevPoint : prevPoints) {
            if (prevPoint.varTable.getDef(idx) != def) {
                continue;
            }

            VarStore store = prevPoint.varTable.getStore(idx);
            if (store != null) {
                result = store.getStoreVarStage() < prevPoint.varStage;
                if (result) {
                    break;
                }
                continue;
            }
            if (prevPoint.varStage > 0) {
                result = true;
                break;
            }
            if (visited.contains(prevPoint)) {
                continue;
            }
            visited.add(prevPoint);
            result = prevPoint.markAsyncAccordingPrevStore(def, visited);
            visited.remove(prevPoint);
            if (result) {
                break;
            }
        }
        return result;
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

    VarTable computeVarForFrame() {
        varTable.fixGlobalId();
        varTable.setStackSizeForFrame(initStackIns == null ? 0 : initStackIns.length);
        return varTable;
    }

    public int getStackStashSize() {
        return stackStashSize;
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
        next.addPrevPoint(this);
    }

    private void addPrevPoint(CodeEnterPoint prev) {
        if (prevPoints == null) {
            prevPoints = new ArrayList<>();
        }
        prevPoints.add(prev);
    }

    public void sortEntries() {
        if (prevPoints != null) {
            prevPoints.sort(Comparator.comparing(CodeEnterPoint::getCodeIdx));
        }
        if (nextPoints != null) {
            nextPoints.sort(Comparator.comparing(CodeEnterPoint::getCodeIdx));
        }
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
        Exp in = ins[--sp];
        ins[sp] = null;

        if (in instanceof FunctionCall && ((FunctionCall) in).isAsync()) {
            in.setDist(ResDist.POP);
            addCode(Pop.of(false));
            return;
        }

        if (in instanceof ConstCall && ((ConstCall) in).isAsync()) {
            in.setDist(ResDist.POP);
            addCode(Pop.of(false));
            return;
        }
        addCode(Pop.of(true));
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
        ins[sp - 1] = instrument;
        addCode(instrument);
    }

    public void expArray() {
        ExpObjArray instrument = ExpObjArray.of(false, ins[--sp]);
        ins[sp - 1] = instrument;
        addCode(instrument);
    }

    public void pushArray() {
        PushArray instrument = PushArray.of(ins[--sp]);
        ins[sp - 1] = instrument;
        addCode(instrument);
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

    private FunctionCall function0(int funcId, int argCount, boolean spread, int asyncPoint) {
        int sp = this.sp;
        if (spread) {
            Assert.isTrue(argCount == 1);
            Assert.isTrue(sp >= 1);
            FunctionCall fc = FunctionCall.spread(funcId, ins[sp - 1], asyncPoint);
            addCode(ins[sp - 1] = fc);
            return fc;
        } else {
            sp -= argCount;
            Assert.isTrue(sp >= 0);
            FunctionCall fc = FunctionCall.of(funcId, Arrays.copyOfRange(ins, sp, sp + argCount), asyncPoint);
            addCode(ins[sp] = fc);
            this.sp = sp + 1;
            return fc;
        }
    }

    void function(int funcId, int argCount, boolean spread) {
        int stashSize = spread ? 1 : argCount;
        stackStashSize = Integer.max(stackStashSize, stashSize);
        FunctionCall fc = function0(funcId, argCount, spread, -1);
        fc.setStashStackSize(stashSize);
    }

    public void asyncFunction(int asyncFuncId, int asyncPoint, int argCount, boolean spread) {
        Assert.isTrue(asyncPoint >= 0);
        varStage++;
        int sp = this.sp;
        stackStashSize = Integer.max(stackStashSize, sp);
        FunctionCall fc = function0(asyncFuncId, argCount, spread, asyncPoint);
        fc.setRestoreStackSize(this.sp - 1);
        fc.setStashStackSize(sp);
    }

    public void constant(int constId, int asyncPoint) {
        if (asyncPoint >= 0) {
            varStage++;
        }
        Assert.isTrue(sp >= 0);
        ConstCall constCall = ConstCall.of(constId, asyncPoint);
        constCall.setPrevStackSize(sp);
        addCode(ins[sp++] = constCall);
    }

    private boolean optimizeConditionJump(Exp in) {
        if (cl < 1 || codes[cl - 1] != in) {
            return false;
        }
        if ((in instanceof Binary)) {
            if (((Binary) in).canOptimiseIf()) {
                ((Binary) in).setOptimiseIf();
                return true;
            }
            return false;
        }

        if (in instanceof Unary && ((Unary) in).getType() == Unary.Type.NEG) {
            ((Unary) in).setOptimiseIf();
        }

        if(in instanceof IterateNext){
            ((IterateNext) in).setOptimiseIf();
            return true;
        }

        return false;
    }

    public void conditionalJump(boolean trueJump, CodeEnterPoint target) {
        Exp in = ins[--sp];
        ConditionalJump instrument = ConditionalJump.of(in, target, trueJump);
        if (optimizeConditionJump(in)) {
            instrument.setOptimiseIf();
        }
        addCode(instrument);
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

    void asmCodes(ClzAssembler clzAssembler) {
        clzAssembler.visitLabel(getStartLabel());

        if (writeFrame) {
            clzAssembler.writeFrame(varTable, catchPoint);
        }

        int sp = getInitStackSize();
        for (Instrument code : getCodes()) {
            sp += code.assemble(clzAssembler);
        }
        clzAssembler.visitLabel(getEndLabel());
        if (CollectionUtils.isEmpty(getNextPoints())) {
            Assert.isTrue(sp == 0);
        } else {
            for (CodeEnterPoint nextPoint : getNextPoints()) {
                Assert.isTrue(sp == nextPoint.getInitStackSize());
            }
        }
    }

    Label getEndLabel() {
        return endLabel;
    }

    Label getStartLabel() {
        return startLabel;
    }


}