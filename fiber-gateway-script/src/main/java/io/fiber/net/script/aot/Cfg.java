package io.fiber.net.script.aot;

import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class Cfg {
    private final TreeMap<Integer, Block> blockTreeMap = new TreeMap<>();

    public void addBlock(int pc) {
        Integer idx = pc;
        if (blockTreeMap.containsKey(idx)) {
            return;
        }
        blockTreeMap.put(idx, new Block(pc));
    }

    public Block mustFindBlock(int pc) {
        return Objects.requireNonNull(blockTreeMap.floorEntry(pc).getValue());
    }

    public static class Builder {

        Cfg cfg;
        final Compiled compiled;


        public Builder(Compiled compiled) {
            this.compiled = compiled;
        }

        private void addBlock(int pc) {
            if (pc < compiled.getCodes().length) {
                cfg.addBlock(pc);
            }
        }

        private Block mustGetByPc(int pc) {
            return Objects.requireNonNull(cfg.blockTreeMap.get(pc));
        }

        public void build() {
            cfg = new Cfg();
            addBlock(0);
            int[] codes = compiled.getCodes();
            for (int i = 0; i < codes.length; i++) {
                int code = codes[i];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP:
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        addBlock(code >>> 8);
                        addBlock(i + 1);
                        break;
                    }
                    case Code.INTO_CATCH: {
                        addBlock(i);
                    }
                    case Code.THROW_EXP:
                    case Code.END_RETURN:
                        addBlock(i + 1);
                        break;
                    default:
                        break;
                }
                Instruction.Throw aThrow = getThrow(c);
                if (aThrow == Instruction.Throw.MAYBE) {
                    addBlock(i + 1);
                }
            }

            Block last = null;
            for (Map.Entry<Integer, Block> entry : cfg.blockTreeMap.entrySet()) {
                Block value = entry.getValue();
                if (last != null) {
                    last.endPc = value.startPc;
                }
                last = value;
            }

            for (int i = 0; i < codes.length; i++) {
                Block current = cfg.mustFindBlock(i);
                int code = codes[i];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP: {
                        Block block = mustGetByPc(code >>> 8);
                        Assert.isTrue(current != block);
                        new Link(Link.Type.JUMP, current, block);
                        break;
                    }
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        Block block = mustGetByPc(code >>> 8);
                        Assert.isTrue(current != block);
                        new Link(Link.Type.JUMP, current, block);
                        Block next = mustGetByPc(i + 1);
                        Assert.isTrue(current != next);
                        new Link(Link.Type.FALLTHROUGH, current, next);
                        break;
                    }
                    case Code.THROW_EXP:
                    case Code.END_RETURN:
                        addBlock(i + 1);
                        break;
                    default:
                        break;
                }
                Instruction.Throw aThrow = getThrow(c);
                if (aThrow != Instruction.Throw.NOT) {
                    int cpc = Compiled.searchExpHandle(i, compiled.getExpIns());
                    if (cpc >= 0) {
                        Block catchPoint = mustGetByPc(cpc);
                        Assert.isTrue(current != catchPoint);
                        new Link(Link.Type.THROW, current, catchPoint);
                    }
                    if (aThrow == Instruction.Throw.MAYBE) {
                        Block next = mustGetByPc(i + 1);
                        Assert.isTrue(current != next);
                        new Link(Link.Type.FALLTHROUGH, current, next);
                    }
                }
            }
        }

        public static Instruction.Throw getThrow(int c) {
            switch (c) {
                case Code.NOOP:
                case Code.LOAD_CONST:
                case Code.LOAD_ROOT:
                case Code.DUMP:
                case Code.POP:
                case Code.LOAD_VAR:
                case Code.STORE_VAR:

                case Code.NEW_OBJECT:
                case Code.NEW_ARRAY:
                case Code.EXP_OBJECT:
                case Code.EXP_ARRAY:
                case Code.PUSH_ARRAY:
                    return Instruction.Throw.NOT;

                //
                case Code.IDX_GET:
                    return Instruction.Throw.NOT;
                case Code.IDX_SET:
                case Code.IDX_SET_1:
                    return Instruction.Throw.MAYBE;
                case Code.PROP_GET:
                    return Instruction.Throw.NOT;
                case Code.PROP_SET:
                case Code.PROP_SET_1:
                    return Instruction.Throw.MAYBE;
                // hack 必需保持递增
                case Code.BOP_PLUS:
                case Code.BOP_MINUS:
                case Code.BOP_MULTIPLY:
                case Code.BOP_DIVIDE:
                case Code.BOP_MOD:
                    return Instruction.Throw.MAYBE;

                case Code.BOP_MATCH:
                case Code.BOP_LT:
                case Code.BOP_LTE:
                case Code.BOP_GT:
                case Code.BOP_GTE:
                case Code.BOP_EQ:
                case Code.BOP_SEQ:
                case Code.BOP_NE:
                case Code.BOP_SNE:
                case Code.BOP_IN:
                    return Instruction.Throw.NOT;
                // hack 必需保持递增
                case Code.UNARY_PLUS:
                case Code.UNARY_MINUS:
                    return Instruction.Throw.MAYBE;

                case Code.UNARY_NEG:
                case Code.UNARY_TYPEOF:
                    return Instruction.Throw.NOT;
                case Code.CALL_FUNC:
                case Code.CALL_FUNC_SPREAD:
                case Code.CALL_ASYNC_FUNC:
                case Code.CALL_ASYNC_FUNC_SPREAD:
                case Code.CALL_CONST:
                case Code.CALL_ASYNC_CONST:
                    return Instruction.Throw.MAYBE;
                //
                case Code.JUMP:
                case Code.JUMP_IF_FALSE:
                case Code.JUMP_IF_TRUE:

                case Code.ITERATE_INTO:
                case Code.ITERATE_NEXT:
                case Code.ITERATE_KEY:
                case Code.ITERATE_VALUE:
                case Code.INTO_CATCH:
                    return Instruction.Throw.NOT;

                case Code.THROW_EXP:
                    return Instruction.Throw.ALWAYS;
                case Code.END_RETURN:
                    return Instruction.Throw.NOT;
                default:
                    throw new IllegalStateException("[bug]unknown code");
            }
        }
    }
}
