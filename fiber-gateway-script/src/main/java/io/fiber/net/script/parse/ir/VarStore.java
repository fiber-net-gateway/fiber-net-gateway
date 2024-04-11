package io.fiber.net.script.parse.ir;

abstract class VarStore extends Instrument {
    private VarTable.VarDef storeVar;
    private int storeVarStage;
    private final int storeIdx;
    private int codeIdx;
    VarStore prevStore;
    private CodeEnterPoint codeEnterPoint;

    VarStore setPrevStore(VarStore prevStore) {
        this.prevStore = prevStore;
        return this;
    }

    void setCodeEnterPoint(CodeEnterPoint codeEnterPoint) {
        this.codeEnterPoint = codeEnterPoint;
    }

    VarStore getPrevStore() {
        return prevStore;
    }

    CodeEnterPoint getCodeEnterPoint() {
        return codeEnterPoint;
    }

    public int getCodeIdx() {
        return codeIdx;
    }

    public void setCodeIdx(int codeIdx) {
        this.codeIdx = codeIdx;
    }

    int getStoreIdx() {
        return storeIdx;
    }

    VarTable.VarDef getStoreVar() {
        return storeVar;
    }

    void setStoreVar(VarTable.VarDef storeVar) {
        this.storeVar = storeVar;
    }

    VarStore(int storeIdx) {
        this.storeIdx = storeIdx;
    }

    int getStoreVarStage() {
        return storeVarStage;
    }

    void setStoreVarStage(int storeVarStage) {
        this.storeVarStage = storeVarStage;
    }
}
