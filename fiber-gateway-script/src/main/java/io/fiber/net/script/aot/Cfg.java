package io.fiber.net.script.aot;

import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.Code;

import java.util.*;

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

            {
                Block last = null;
                for (Map.Entry<Integer, Block> entry : cfg.blockTreeMap.entrySet()) {
                    Block value = entry.getValue();
                    if (last != null) {
                        last.endPc = value.startPc;
                    }
                    last = value;
                }
                if (last != null) {
                    last.endPc = codes.length;
                }
            }

            for (int i = 0; i < codes.length; i++) {
                Block current = cfg.mustFindBlock(i);
                int code = codes[i];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP: {
                        Block block = mustGetByPc(code >>> 8);
                        Assert.isTrue(current != block);
                        new Edge(Edge.Type.JUMP, current, block);
                        break;
                    }
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        Block block = mustGetByPc(code >>> 8);
                        Assert.isTrue(current != block);
                        new Edge(Edge.Type.JUMP, current, block);
                        Block next = mustGetByPc(i + 1);
                        Assert.isTrue(current != next);
                        new Edge(Edge.Type.FALLTHROUGH, current, next);
                        break;
                    }
                    default:
                        break;
                }
                Instruction.Throw aThrow = getThrow(c);
                if (aThrow != Instruction.Throw.NOT) {
                    int cpc = Compiled.searchExpHandle(i, compiled.getExpIns());
                    if (cpc >= 0) {
                        Block catchPoint = mustGetByPc(cpc);
                        Assert.isTrue(current != catchPoint);
                        new Edge(Edge.Type.THROW, current, catchPoint);
                    }
                    if (aThrow == Instruction.Throw.MAYBE) {
                        Block next = mustGetByPc(i + 1);
                        Assert.isTrue(current != next);
                        new Edge(Edge.Type.FALLTHROUGH, current, next);
                    }
                }
            }

            Set<Block> visited = Collections.newSetFromMap(new IdentityHashMap<>());


            MockInv inv = new MockInv(compiled);
            int sp = 0;
            int pc = 0;
            Queue<Block> queue = new ArrayDeque<>();
            queue.add(mustGetByPc(pc));
            do {
                Block b = queue.poll();
                visited.add(b);
                for (int i = b.startPc; i < b.endPc; i++) {

                }

                for (Edge edge : b.successors) {
                    if (!visited.contains(edge.successor)) {
                        queue.add(edge.successor);
                    }
                }

            } while (!queue.isEmpty());


        }



        private static class MockInv {
            final Compiled compiled;
            final Expr[] stack;
            final Var[] vars;
            int sp;
            boolean[] varUse;
            boolean[] varDef;
            int varVersion;

            public MockInv(Compiled compiled) {
                this.compiled = compiled;
                this.stack = new Expr[compiled.getStackSize()];
                this.vars = new Var[compiled.getVarTableSize()];
            }

            Var loadVar(int idx) {
                Var var = vars[idx];
                if (var != null) {
                    return var;
                }
                return vars[idx] = new Var(idx);
            }

            public void clearStack() {
                sp = 0;
            }

            public void invoke(Block block) {
                varUse = new boolean[compiled.getVarTableSize()];
                varDef = new boolean[compiled.getVarTableSize()];

                int[] codes = compiled.getCodes();
                for (int i = block.startPc; i < block.endPc; i++) {
                    int code = codes[i];
                    int ins = code & 0xFF;
                    switch (ins) {
                        case Code.NOOP:
                            break;
                        case Code.LOAD_CONST:
                            stack[sp++] = new LoadConst(block, i, (ValueNode) compiled.getOperands()[code >>> 8]);
                            break;
                        case Code.LOAD_ROOT:
                            stack[sp++] = new LoadRoot(block, i);
                            break;
                        case Code.DUMP:
                            stack[sp] = stack[sp - 1];
                            sp++;
                            break;
                        case Code.POP:
                            sp--;
                            break;
                        case Code.LOAD_VAR:
                            stack[sp++] = loadVar(code >>> 8);
                            break;
                        case Code.STORE_VAR:
                            break;
                        case Code.NEW_OBJECT:
                            break;
                        case Code.NEW_ARRAY:
                            break;
                        case Code.EXP_OBJECT:
                            break;
                        case Code.EXP_ARRAY:
                            break;
                        case Code.PUSH_ARRAY:
                            break;
                        //
                        case Code.IDX_GET://!
                            break;
                        case Code.IDX_SET://!
                            break;
                        case Code.IDX_SET_1://!
                            break;
                        case Code.PROP_GET://!
                            break;
                        case Code.PROP_SET://! {a:1}
                            break;
                        case Code.PROP_SET_1://! {a:1}
                            break;
                        //
                        case Code.BOP_PLUS:
                        case Code.BOP_MINUS:
                        case Code.BOP_MULTIPLY:
                        case Code.BOP_DIVIDE:
                        case Code.BOP_MOD:
                        case Code.BOP_MATCH: //~

                        case Code.BOP_LT: //<
                        case Code.BOP_LTE: //<=
                        case Code.BOP_GT: //~
                        case Code.BOP_GTE: //~
                        case Code.BOP_EQ: //~
                        case Code.BOP_SEQ: //~
                        case Code.BOP_NE: //~
                        case Code.BOP_SNE: //~
                        case Code.BOP_IN: // in
                            break;
                        //
                        case Code.UNARY_PLUS:
                        case Code.UNARY_MINUS:
                        case Code.UNARY_NEG://!
                        case Code.UNARY_TYPEOF://!
                            break;
                        case Code.CALL_FUNC: {
                            break;
                        }
                        case Code.CALL_FUNC_SPREAD: {
                            break;
                        }

                        case Code.CALL_ASYNC_FUNC: {
                            break;
                        }
                        case Code.CALL_ASYNC_FUNC_SPREAD: {
                            break;
                        }
                        case Code.CALL_CONST:
                            break;
                        case Code.CALL_ASYNC_CONST:
                            break;
                        case Code.JUMP:
                            break;
                        case Code.JUMP_IF_FALSE:
                            break;
                        case Code.JUMP_IF_TRUE:
                            break;
                        case Code.ITERATE_INTO:
                            break;
                        case Code.ITERATE_NEXT:
                            break;
                        case Code.ITERATE_KEY:
                            break;
                        case Code.ITERATE_VALUE:
                            break;

                        case Code.INTO_CATCH:
                            break;
                        case Code.THROW_EXP:
                            break;
                        case Code.END_RETURN:
                            break;
                        default:
                            throw new IllegalStateException("unknown code:" + code);
                    }
                }
            }

        }
        /*
            for (int i = 0; i < codes.length; i++) {
                Block current = cfg.mustFindBlock(i);
                int code = codes[i];
                int ins = code & 0xFF;
                switch (ins) {
                    case Code.NOOP:
                        break;
                    case Code.LOAD_CONST:
                        new LoadConst(current, i, (ValueNode) compiled.getOperands()[code >>> 8]);
                        break;
                    case Code.LOAD_ROOT:
                        break;
                    case Code.DUMP:
                        break;
                    case Code.POP:
                        break;
                    case Code.LOAD_VAR:
                        break;
                    case Code.STORE_VAR:
                        break;
                    case Code.NEW_OBJECT:
                        break;
                    case Code.NEW_ARRAY:
                        break;
                    case Code.EXP_OBJECT:
                        break;
                    case Code.EXP_ARRAY:
                        break;
                    case Code.PUSH_ARRAY:
                        break;
                    //
                    case Code.IDX_GET://!
                        break;
                    case Code.IDX_SET://!
                        break;
                    case Code.IDX_SET_1://!
                        break;
                    case Code.PROP_GET://!
                        break;
                    case Code.PROP_SET://! {a:1}
                        break;
                    case Code.PROP_SET_1://! {a:1}
                        break;
                    //
                    case Code.BOP_PLUS:
                    case Code.BOP_MINUS:
                    case Code.BOP_MULTIPLY:
                    case Code.BOP_DIVIDE:
                    case Code.BOP_MOD:
                    case Code.BOP_MATCH: //~

                    case Code.BOP_LT: //<
                    case Code.BOP_LTE: //<=
                    case Code.BOP_GT: //~
                    case Code.BOP_GTE: //~
                    case Code.BOP_EQ: //~
                    case Code.BOP_SEQ: //~
                    case Code.BOP_NE: //~
                    case Code.BOP_SNE: //~
                    case Code.BOP_IN: // in
                        break;
                    //
                    case Code.UNARY_PLUS:
                    case Code.UNARY_MINUS:
                    case Code.UNARY_NEG://!
                    case Code.UNARY_TYPEOF://!
                        break;
                    case Code.CALL_FUNC: {
                        break;
                    }
                    case Code.CALL_FUNC_SPREAD: {
                        break;
                    }

                    case Code.CALL_ASYNC_FUNC: {
                        break;
                    }
                    case Code.CALL_ASYNC_FUNC_SPREAD: {
                        break;
                    }
                    case Code.CALL_CONST:
                        break;
                    case Code.CALL_ASYNC_CONST:
                        break;
                    case Code.JUMP:
                        break;
                    case Code.JUMP_IF_FALSE:
                        break;
                    case Code.JUMP_IF_TRUE:
                        break;
                    case Code.ITERATE_INTO:
                        break;
                    case Code.ITERATE_NEXT:
                        break;
                    case Code.ITERATE_KEY:
                        break;
                    case Code.ITERATE_VALUE:
                        break;

                    case Code.INTO_CATCH:
                        break;
                    case Code.THROW_EXP:
                        break;
                    case Code.END_RETURN:
                        break;
                    default:
                        throw new IllegalStateException("unknown code:" + code);
                }
            }

         */

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
