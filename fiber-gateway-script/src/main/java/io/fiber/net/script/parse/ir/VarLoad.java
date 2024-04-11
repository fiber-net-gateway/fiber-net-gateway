package io.fiber.net.script.parse.ir;

interface VarLoad {
    VarTable.VarDef getLoadVar();

    void setLoadVar(VarTable.VarDef loadVar);

    int getLoadIdx();

    void setLoadVarStage(int stage);

    int getLoadVarStage();

    int getCodeIdx();

    void setCodeIdx(int codeIdx);

}
