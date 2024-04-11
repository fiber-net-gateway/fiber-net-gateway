package io.fiber.net.script.parse.ir;

class LoadVar extends Exp implements VarLoad {
    private final int varIdx;
    private int loadVarStage;

    private VarTable.VarDef loadVar;
    private int codeIdx;

    @Override
    public int getCodeIdx() {
        return codeIdx;
    }

    @Override
    public void setCodeIdx(int codeIdx) {
        this.codeIdx = codeIdx;
    }

    LoadVar(int varIdx) {
        this.varIdx = varIdx;
    }

    static LoadVar of(int varIdx) {
        return new LoadVar(varIdx);
    }

    @Override
    public VarTable.VarDef getLoadVar() {
        return loadVar;
    }

    @Override
    public void setLoadVar(VarTable.VarDef loadVar) {
        this.loadVar = loadVar;
    }

    @Override
    public int getLoadIdx() {
        return varIdx;
    }

    @Override
    public void setLoadVarStage(int stage) {
        loadVarStage = stage;
    }

    @Override
    public int getLoadVarStage() {
        return loadVarStage;
    }

    @Override
    void accept(InstrumentVisitor visitor) {
        visitor.visitLoadVar(this);
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.loadVar(getLoadVar());
        return 1;
    }
}
