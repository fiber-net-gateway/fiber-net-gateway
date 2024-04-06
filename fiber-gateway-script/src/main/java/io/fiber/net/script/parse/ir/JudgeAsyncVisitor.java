package io.fiber.net.script.parse.ir;

class JudgeAsyncVisitor implements InstrumentVisitor {
    private boolean async;

    boolean visitAndGet(Exp exp) {
        async = false;
        exp.accept(this);
        return async;
    }

    private void visit0(Exp... exps) {
        if (async) {
            return;
        }
        for (Exp exp : exps) {
            exp.accept(this);
            if (async) {
                return;
            }
        }
    }

    @Override
    public void visitBinary(Binary ins) {
        visit0(ins.getLeft(), ins.getRight());
    }

    @Override
    public void visitConditionalJump(ConditionalJump ins) {
        visit0(ins.getPredict());
    }

    @Override
    public void visitConstCall(ConstCall ins) {
        async = ins.isAsync();
    }

    @Override
    public void visitDump(Dump ins) {

    }

    @Override
    public void visitExpObjArray(ExpObjArray ins) {
        visit0(ins.getAddition());
    }

    @Override
    public void visitFunctionCall(FunctionCall ins) {
        async = ins.isAsync();
    }

    @Override
    public void visitIndexGet(IndexGet ins) {
        visit0(ins.getParent(), ins.getKey());
    }

    @Override
    public void visitIndexSet(IndexSet ins) {
        visit0(ins.getAlien(), ins.getParent(), ins.getKey());
    }

    @Override
    public void visitIntoCatch(IntoCatch ins) {

    }

    @Override
    public void visitIterateInto(IterateInto ins) {

    }

    @Override
    public void visitIterateNext(IterateNext ins) {

    }

    @Override
    public void visitIterateVar(IterateVar ins) {

    }

    @Override
    public void visitJump(Jump ins) {

    }

    @Override
    public void visitLoadConst(LoadConst ins) {

    }

    @Override
    public void visitLoadRoot(LoadRoot ins) {

    }

    @Override
    public void visitLoadVar(LoadVar ins) {

    }

    @Override
    public void visitNewObjArray(NewObjArray ins) {

    }

    @Override
    public void visitNoop(Noop ins) {

    }

    @Override
    public void visitPop(Pop ins) {

    }

    @Override
    public void visitPropGet(PropGet ins) {
        visit0(ins.getParent());
    }

    @Override
    public void visitPropSet(PropSet ins) {
        visit0(ins.getParent(), ins.getAlien());
    }

    @Override
    public void visitPropSet_1(PropSet_1 ins) {
        visit0(ins.getParent(), ins.getAlien());
    }

    @Override
    public void visitPushArray(PushArray ins) {
        visit0(ins.getAddition());
    }

    @Override
    public void visitStackRef(StackRef ins) {

    }

    @Override
    public void visitReturn(Return ins) {
        if (ins.getTarget() != null) {
            visit0(ins.getTarget());
        }
    }

    @Override
    public void visitReturnV(ReturnV ins) {

    }

    @Override
    public void visitStoreVar(StoreVar ins) {
        visit0(ins.getValue());
    }

    @Override
    public void visitThrow(Throw ins) {
        visit0(ins.getTarget());
    }

    @Override
    public void visitUnary(Unary ins) {
        visit0(ins.getExp());
    }
}
