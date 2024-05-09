package io.fiber.net.script.parse.ir;

class IterateNext extends Exp implements VarLoad {

    private final int varIdx;
    private VarTable.VarDef iteratorVar;
    private int loadVarStage;
    private int codeIdx;
    private boolean optimiseIf;

    @Override
    public int getCodeIdx() {
        return codeIdx;
    }

    @Override
    public void setCodeIdx(int codeIdx) {
        this.codeIdx = codeIdx;
    }

    IterateNext(int varIdx) {
        this.varIdx = varIdx;
    }

    public static IterateNext of(int varIdx) {
        return new IterateNext(varIdx);
    }

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

    void setOptimiseIf() {
        this.optimiseIf = true;
    }

    public boolean isOptimiseIf() {
        return optimiseIf;
    }

    @Override
    int assemble(ClzAssembler assembler) {
        assembler.iterateNext(getLoadVar(), isOptimiseIf());
        return 1;
    }
}
