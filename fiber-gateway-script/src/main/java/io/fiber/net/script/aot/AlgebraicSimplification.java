package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.json.ValueNode;

public class AlgebraicSimplification {

    private final Cfg cfg;

    public AlgebraicSimplification(Cfg cfg) {
        this.cfg = cfg;
    }

    public boolean optimize() {
        boolean changed = false;
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions().toArray(new Instruction[0])) {
                if (instruction instanceof Unary) {
                    changed |= simplifyUnary(block, (Unary) instruction);
                } else if (instruction instanceof Binary) {
                    changed |= simplifyBinary(block, (Binary) instruction);
                }
            }
        }
        return changed;
    }

    private boolean simplifyUnary(Block block, Unary unary) {
        SsaValue material = unary.getMaterial();
        switch (unary.getOp()) {
            case PLUS:
                if (material.getType() == SsaValue.Type.NUMBER) {
                    replaceWithValue(block, unary, material);
                    return true;
                }
                return false;
            case NEG: {
                Expr assign = material.getAssign();
                if (assign instanceof Unary) {
                    Unary inner = (Unary) assign;
                    if (inner.getOp() == Unary.Op.NEG && inner.getMaterial().getType() == SsaValue.Type.BOOLEAN) {
                        replaceWithValue(block, unary, inner.getMaterial());
                        return true;
                    }
                }
                return false;
            }
            case TYPEOF: {
                String typeName = typeName(material.getType());
                if (typeName == null) {
                    return false;
                }
                replaceWithConst(block, unary, TextNode.valueOf(typeName));
                return true;
            }
            default:
                return false;
        }
    }

    private boolean simplifyBinary(Block block, Binary binary) {
        SsaValue left = binary.getLeft();
        SsaValue right = binary.getRight();
        boolean numbers = left.getType() == SsaValue.Type.NUMBER && right.getType() == SsaValue.Type.NUMBER;
        if (!numbers) {
            return false;
        }

        ValueNode leftConst = constantValue(left);
        ValueNode rightConst = constantValue(right);
        switch (binary.getOp()) {
            case PLUS:
                if (isZero(rightConst)) {
                    replaceWithValue(block, binary, left);
                    return true;
                }
                if (isZero(leftConst)) {
                    replaceWithValue(block, binary, right);
                    return true;
                }
                return false;
            case MINUS:
                if (isZero(rightConst)) {
                    replaceWithValue(block, binary, left);
                    return true;
                }
                return false;
            case MULTIPLY:
                if (isOne(rightConst)) {
                    replaceWithValue(block, binary, left);
                    return true;
                }
                if (isOne(leftConst)) {
                    replaceWithValue(block, binary, right);
                    return true;
                }
                if (isZero(rightConst) || isZero(leftConst)) {
                    replaceWithConst(block, binary, IntNode.valueOf(0));
                    return true;
                }
                return false;
            case DIVIDE:
                if (isOne(rightConst)) {
                    replaceWithValue(block, binary, left);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void replaceWithValue(Block block, Expr expr, SsaValue replacement) {
        expr.dropOperands();
        cfg.replaceValue(expr.getResult(), replacement);
        block.removeInstruction(expr);
    }

    private static void replaceWithConst(Block block, Expr expr, ValueNode value) {
        LoadConst replacement = new LoadConst(block, expr.getPc(), value);
        expr.dropOperands();
        expr.getResult().replaceAssign(replacement);
        block.replaceInstruction(expr, replacement);
    }

    private static ValueNode constantValue(SsaValue value) {
        Expr assign = value.getAssign();
        return assign instanceof LoadConst ? ((LoadConst) assign).getValueNode() : null;
    }

    private static boolean isZero(ValueNode node) {
        return node != null && node.isNumber() && node.doubleValue() == 0D;
    }

    private static boolean isOne(ValueNode node) {
        return node != null && node.isNumber() && node.doubleValue() == 1D;
    }

    private static String typeName(SsaValue.Type type) {
        switch (type) {
            case ARRAY:
            case BINARY:
            case BOOLEAN:
            case MISSING:
            case NULL:
            case NUMBER:
            case OBJECT:
            case STRING:
            case EXCEPTION:
            case ITERATOR:
                return type.name().toLowerCase();
            default:
                return null;
        }
    }
}
