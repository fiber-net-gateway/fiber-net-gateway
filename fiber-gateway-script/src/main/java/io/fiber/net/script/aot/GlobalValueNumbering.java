package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GlobalValueNumbering {

    private final Cfg cfg;
    private final OptimizationContext context;
    private final Map<Key, SsaValue> available = new HashMap<>();
    private final List<Undo> undoLog = new ArrayList<>();
    private boolean changed;

    public GlobalValueNumbering(Cfg cfg) {
        this(cfg, new OptimizationContext(cfg));
    }

    GlobalValueNumbering(Cfg cfg, OptimizationContext context) {
        this.cfg = cfg;
        this.context = context;
    }

    public boolean optimize() {
        Dominators dominators = context.dominators();
        visitDominatorTree(cfg.getEntryBlock(), dominators);
        return changed;
    }

    private void visitDominatorTree(Block block, Dominators dominators) {
        int mark = undoLog.size();
        for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
            if (instruction.isMemoryBarrier()) {
                applyMemoryBarrier(instruction);
            }
            if (!(instruction instanceof Expr) || !isCseCandidate((Expr) instruction)) {
                continue;
            }
            Expr expr = (Expr) instruction;
            Key key = Key.of(expr);
            SsaValue old = available.get(key);
            if (old == null) {
                put(key, expr.getResult());
                continue;
            }
            expr.dropOperands();
            cfg.replaceValue(expr.getResult(), old);
            block.removeInstruction(expr);
            changed = true;
        }
        List<Block> children = dominators.children.get(block);
        if (children != null) {
            for (Block child : children) {
                visitDominatorTree(child, dominators);
            }
        }
        rollback(mark);
    }

    private void put(Key key, SsaValue value) {
        SsaValue old = available.put(key, value);
        undoLog.add(new Undo(key, old, old != null));
    }

    private void remove(Key key) {
        SsaValue old = available.remove(key);
        undoLog.add(new Undo(key, old, old != null));
    }

    private void rollback(int mark) {
        for (int i = undoLog.size() - 1; i >= mark; i--) {
            Undo undo = undoLog.remove(i);
            if (undo.hadOld) {
                available.put(undo.key, undo.oldValue);
            } else {
                available.remove(undo.key);
            }
        }
    }

    private void applyMemoryBarrier(Instruction instruction) {
        if (instruction instanceof CallFunc || instruction instanceof CallAsyncFunc
                || instruction instanceof CallConst || instruction instanceof CallAsyncConst) {
            removePropertyKeys(null, null, true);
            return;
        }
        if (instruction instanceof PropSet) {
            PropSet propSet = (PropSet) instruction;
            removePropertyKeys(propSet.getOwner(), propSet.getKey(), false);
            return;
        }
        if (instruction instanceof PropSet1) {
            PropSet1 propSet = (PropSet1) instruction;
            removePropertyKeys(propSet.getOwner(), propSet.getKey(), false);
            return;
        }
        if (instruction instanceof IndexSet) {
            removePropertyKeys(((IndexSet) instruction).getOwner(), null, true);
            return;
        }
        if (instruction instanceof IndexSet1) {
            removePropertyKeys(((IndexSet1) instruction).getOwner(), null, true);
            return;
        }
        if (instruction instanceof ExpandObj) {
            removePropertyKeys(((ExpandObj) instruction).getTarget(), null, true);
            return;
        }
        if (instruction instanceof ExpandArr) {
            removePropertyKeys(((ExpandArr) instruction).getTarget(), null, true);
            return;
        }
        if (instruction instanceof PushArr) {
            removePropertyKeys(((PushArr) instruction).getTarget(), null, true);
        }
    }

    private void removePropertyKeys(SsaValue owner, String propKey, boolean allForOwner) {
        for (Key key : available.keySet().toArray(new Key[0])) {
            if (!key.isPropertyKey()) {
                continue;
            }
            if (owner != null && key.left != owner) {
                continue;
            }
            if (!allForOwner && key.type == PropGet.class && Objects.equals(key.op, propKey)) {
                remove(key);
            } else if (allForOwner || owner == null || key.type == IndexGet.class) {
                remove(key);
            }
        }
    }

    private static boolean isCseCandidate(Expr expr) {
        if (!expr.isRemovablePure()) {
            return false;
        }
        return expr instanceof LoadConst
                || expr instanceof LoadRoot
                || expr instanceof Unary
                || expr instanceof Binary
                || expr instanceof PropGet
                || expr instanceof IndexGet;
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
            if (expr instanceof Binary) {
                Binary binary = (Binary) expr;
                SsaValue left = binary.getLeft();
                SsaValue right = binary.getRight();
                if (isCommutative(binary) && System.identityHashCode(left) > System.identityHashCode(right)) {
                    SsaValue tmp = left;
                    left = right;
                    right = tmp;
                }
                return new Key(Binary.class, binary.getOp(), null, left, right);
            }
            if (expr instanceof PropGet) {
                PropGet propGet = (PropGet) expr;
                return new Key(PropGet.class, propGet.getKey(), null, propGet.getOwner(), null);
            }
            IndexGet indexGet = (IndexGet) expr;
            return new Key(IndexGet.class, null, null, indexGet.getOwner(), indexGet.getKey());
        }

        private static boolean isCommutative(Binary binary) {
            switch (binary.getOp()) {
                case PLUS:
                    return binary.getLeft().getType() == SsaValue.Type.NUMBER
                            && binary.getRight().getType() == SsaValue.Type.NUMBER;
                case MULTIPLY:
                case EQ:
                case SEQ:
                case NE:
                case SNE:
                    return true;
                default:
                    return false;
            }
        }

        private boolean isPropertyKey() {
            return type == PropGet.class || type == IndexGet.class;
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

    private static final class Undo {
        private final Key key;
        private final SsaValue oldValue;
        private final boolean hadOld;

        private Undo(Key key, SsaValue oldValue, boolean hadOld) {
            this.key = key;
            this.oldValue = oldValue;
            this.hadOld = hadOld;
        }
    }
}
