package io.fiber.net.script.parse.ir;

class IterateVar extends VarStore implements VarLoad {

    private final int iteratorIdx;
    private final boolean key;
    private VarTable.VarDef iteratorVar;
    private int loadVarStage;

    @Override
    public VarTable.VarDef getLoadVar() {
        return iteratorVar;
    }

    @Override
    public void setLoadVar(VarTable.VarDef loadVar) {
        iteratorVar = loadVar;
    }

    @Override
    public int getLoadIdx() {
        return iteratorIdx;
    }

    @Override
    public void setLoadVarStage(int stage) {
        loadVarStage = stage;
    }

    @Override
    public int getLoadVarStage() {
        return loadVarStage;
    }

    IterateVar(int iteratorIdx, int storeIdx, boolean key) {
        super(storeIdx);
        this.iteratorIdx = iteratorIdx;
        this.key = key;
    }

    boolean isKey() {
        return key;
    }

    public static IterateVar of(int iteratorIdx, int storeIdx, boolean key) {
        return new IterateVar(iteratorIdx, storeIdx, key);
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitIterateVar(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.iterateVar(this);
        return 0;
    }
}
