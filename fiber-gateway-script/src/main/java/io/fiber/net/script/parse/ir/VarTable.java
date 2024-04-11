package io.fiber.net.script.parse.ir;

import io.fiber.net.common.utils.Assert;

class VarTable {
    private static final VarTable EMPTY = new VarTable(0);

    static {
        EMPTY.setStackSizeForFrame(0);
    }

    static VarTable getInstance(int tbSize) {
        return tbSize == 0 ? EMPTY : new VarTable(tbSize);
    }


    static class VarDef {
        private final int idx;
        private final CodeEnterPoint point;
        private boolean async;
        private int globalIdx = -1;

        VarDef(int idx, CodeEnterPoint point) {
            this.idx = idx;
            this.point = point;
        }

        public int getIdx() {
            return idx;
        }

        boolean isAsync() {
            return async;
        }

        void setAsync() {
            this.async = true;
        }

        int getGlobalIdx() {
            return globalIdx;
        }

        void setGlobalIdx(int globalIdx) {
            this.globalIdx = globalIdx;
        }

        CodeEnterPoint getPoint() {
            return point;
        }

    }

    private final VarDef[] defs;
    private final VarStore[] stores;
    private VarTable prevTableForFrame;

    private int minSelfDefIdx = Integer.MAX_VALUE;
    private int maxVarIdx = -1;
    private int useOuterSyncSize;
    private int useOuterAsyncSize;
    private int maxSyncSize;
    private int maxAsyncSize;
    private int stackSizeForFrame;

    void setStackSizeForFrame(int stackSizeForFrame) {
        this.stackSizeForFrame = stackSizeForFrame;
    }

    int getStackSizeForFrame() {
        return stackSizeForFrame;
    }

    private VarTable(int tabSize) {
        this.defs = new VarDef[tabSize];
        this.stores = new VarStore[tabSize];
    }

    void defAndWrite(VarStore varStore) {
        int varIdx = varStore.getStoreIdx();
        Assert.isTrue(defs[varIdx] == null);
        VarDef varDef = new VarDef(varIdx, varStore.getCodeEnterPoint());
        addStore(varDef, varStore);
        updateSelfDefIdx(varIdx);
    }

    private void updateSelfDefIdx(int varIdx) {
        minSelfDefIdx = Math.min(minSelfDefIdx, varIdx);
    }

    private void updateMaxDefIdx(int varIdx) {
        maxVarIdx = Math.max(maxVarIdx, varIdx);
    }

    void addStore(VarDef def, VarStore varStore) {
        Assert.isTrue(varStore.getStoreVar() == null);
        setDef(def);
        int idx = def.idx;
        Assert.isTrue(varStore.getPrevStore() == null);
        stores[idx] = varStore.setPrevStore(stores[idx]);
        // def.addWrite(varStore);
        varStore.setStoreVar(def);
    }

    public int getUseOuterVarSize() {
        return minSelfDefIdx == Integer.MAX_VALUE ? 0 : minSelfDefIdx;
    }

    public int getTableSize() {
        return maxVarIdx + 1;
    }

    VarStore getStore(int varIdx) {
        return stores[varIdx];
    }

    void setDef(VarDef def) {
        int idx = def.idx;
        if (defs[idx] != null) {
            Assert.isTrue(defs[idx] == def);
        } else {
            defs[idx] = def;
        }
        defs[def.getIdx()] = def;
        updateMaxDefIdx(idx);
    }

    VarDef getDef(int varIdx) {
        return defs[varIdx];
    }

    VarTable getPrevTableForFrame() {
        return prevTableForFrame;
    }

    void setPrevTableForFrame(VarTable prevTableForFrame) {
        this.prevTableForFrame = prevTableForFrame;
    }

    void fixGlobalId() {
        VarDef[] defs = this.defs;
        int syncId = 0, asyncId = 0;
        for (int i = 0; i < getUseOuterVarSize(); i++) {
            VarDef def = defs[i];
            if (def.async) {
                Assert.isTrue(def.globalIdx == asyncId);
                asyncId++;
            } else {
                Assert.isTrue(def.globalIdx == syncId);
                syncId++;
            }
        }

        useOuterSyncSize = syncId;
        useOuterAsyncSize = asyncId;

        for (int i = getUseOuterVarSize(), len = getTableSize(); i < len; i++) {
            VarDef def = defs[i];
            if (def.async) {
                def.setGlobalIdx(asyncId++);
            } else {
                def.setGlobalIdx(syncId++);
            }
        }
        this.maxAsyncSize = asyncId;
        this.maxSyncSize = syncId;
    }

    int getMaxSyncSize() {
        return maxSyncSize;
    }

    int getMaxAsyncSize() {
        return maxAsyncSize;
    }

    int getUseOuterSyncSize() {
        return useOuterSyncSize;
    }

    int getUseOuterAsyncSize() {
        return useOuterAsyncSize;
    }

    int getTableCapacity() {
        return defs.length;
    }
}
