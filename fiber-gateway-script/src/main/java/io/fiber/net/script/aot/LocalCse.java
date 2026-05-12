package io.fiber.net.script.aot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LocalCse {

    private final Cfg cfg;

    public LocalCse(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            Map<Key, SsaValue> values = new HashMap<>();
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (hasSideEffect(instruction)) {
                    values.clear();
                    continue;
                }
                if (!(instruction instanceof Expr) || !isCseCandidate((Expr) instruction)) {
                    continue;
                }
                Expr expr = (Expr) instruction;
                Key key = Key.of(expr);
                SsaValue old = values.get(key);
                if (old == null) {
                    values.put(key, expr.getResult());
                    continue;
                }
                expr.dropOperands();
                cfg.replaceValue(expr.getResult(), old);
                block.removeInstruction(expr);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasSideEffect(Instruction instruction) {
        return !DeadCodeElimination.isRemovablePure(instruction) && !(instruction instanceof Jump);
    }

    private static boolean isCseCandidate(Expr expr) {
        if (expr.canThrow() != Instruction.Throw.NOT) {
            return false;
        }
        return expr instanceof LoadConst
                || expr instanceof LoadRoot
                || expr instanceof Unary
                || expr instanceof Binary;
    }

    private static final class Key {
        private final Class<?> type;
        private final Object op;
        private final Object extra;
        private final SsaValue left;
        private final SsaValue right;

        private Key(Class<?> type, Object op, Object extra, SsaValue left, SsaValue right) {
            this.type = type;
            this.op = op;
            this.extra = extra;
            this.left = left;
            this.right = right;
        }

        static Key of(Expr expr) {
            if (expr instanceof LoadConst) {
                return new Key(LoadConst.class, null, ((LoadConst) expr).getValueNode(), null, null);
            }
            if (expr instanceof LoadRoot) {
                return new Key(LoadRoot.class, null, null, null, null);
            }
            if (expr instanceof Unary) {
                Unary unary = (Unary) expr;
                return new Key(Unary.class, unary.getOp(), null, unary.getMaterial(), null);
            }
            Binary binary = (Binary) expr;
            return new Key(Binary.class, binary.getOp(), null, binary.getLeft(), binary.getRight());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key key = (Key) o;
            return type == key.type
                    && Objects.equals(op, key.op)
                    && Objects.equals(extra, key.extra)
                    && left == key.left
                    && right == key.right;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + Objects.hashCode(op);
            result = 31 * result + Objects.hashCode(extra);
            result = 31 * result + System.identityHashCode(left);
            result = 31 * result + System.identityHashCode(right);
            return result;
        }
    }
}
