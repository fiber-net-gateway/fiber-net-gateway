package io.fiber.net.script.aot;

public class TypePropagation {

    private final Cfg cfg;

    public TypePropagation(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        boolean localChanged;
        do {
            localChanged = false;
            for (Block block : cfg.getBlocks()) {
                for (Phi phi : block.getPhiValues()) {
                    localChanged |= update(phi);
                }
                for (Instruction instruction : block.getInstructions()) {
                    if (instruction instanceof Expr) {
                        localChanged |= update((Expr) instruction);
                    }
                }
            }
            changed |= localChanged;
        } while (localChanged);
        return changed;
    }

    private static boolean update(Expr expr) {
        SsaValue.Type oldType = expr.getResult().getType();
        SsaValue.Type newType = infer(expr);
        if (oldType == newType) {
            return false;
        }
        expr.getResult().setInferredType(newType);
        return true;
    }

    private static SsaValue.Type infer(Expr expr) {
        if (expr instanceof Phi) {
            return inferPhi((Phi) expr);
        }
        return expr.getResultType();
    }

    private static SsaValue.Type inferPhi(Phi phi) {
        SsaValue.Type type = null;
        for (Phi.Case aCase : phi.getCases()) {
            SsaValue.Type current = aCase.value.getType();
            if (current == SsaValue.Type.Unknown) {
                return SsaValue.Type.Unknown;
            }
            if (type == null) {
                type = current;
            } else if (type != current) {
                return SsaValue.Type.Unknown;
            }
        }
        return type == null ? SsaValue.Type.Unknown : type;
    }
}
