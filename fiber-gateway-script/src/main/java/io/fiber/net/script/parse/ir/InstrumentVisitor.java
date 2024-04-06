package io.fiber.net.script.parse.ir;

interface InstrumentVisitor {
    void visitBinary(Binary ins);

    void visitConditionalJump(ConditionalJump ins);

    void visitConstCall(ConstCall ins);

    void visitDump(Dump ins);

    void visitExpObjArray(ExpObjArray ins);

    void visitFunctionCall(FunctionCall ins);

    void visitIndexGet(IndexGet ins);

    void visitIndexSet(IndexSet ins);

    void visitIntoCatch(IntoCatch ins);

    void visitIterateInto(IterateInto ins);

    void visitIterateNext(IterateNext ins);

    void visitIterateVar(IterateVar ins);

    void visitJump(Jump ins);

    void visitLoadConst(LoadConst ins);

    void visitLoadRoot(LoadRoot ins);

    void visitLoadVar(LoadVar ins);

    void visitNewObjArray(NewObjArray ins);

    void visitNoop(Noop ins);

    void visitPop(Pop ins);

    void visitPropGet(PropGet ins);

    void visitPropSet(PropSet ins);

    void visitPropSet_1(PropSet_1 ins);

    void visitPushArray(PushArray ins);

    void visitStackRef(StackRef ins);

    void visitReturn(Return ins);

    void visitReturnV(ReturnV ins);

    void visitStoreVar(StoreVar ins);

    void visitThrow(Throw ins);

    void visitUnary(Unary ins);
}
