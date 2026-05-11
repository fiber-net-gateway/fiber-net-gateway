package io.fiber.net.script.aot;

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

    Block mustGetBlock(int pc) {
        return Objects.requireNonNull(blockTreeMap.get(pc));
    }

    public Collection<Block> getBlocks() {
        return blockTreeMap.values();
    }

    private static void addEdge(Edge.Type type, Block predecessor, Block successor) {
        for (Edge edge : predecessor.successors) {
            if (edge.type == type && edge.successor == successor) {
                return;
            }
        }
        new Edge(type, predecessor, successor);
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
            return cfg.mustGetBlock(pc);
        }

        public Cfg build() {
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

            for (Block current : cfg.blockTreeMap.values()) {
                int pc = current.endPc - 1;
                if (pc < current.startPc) {
                    continue;
                }
                int code = codes[pc];
                int c = code & 0xFF;
                switch (c) {
                    case Code.JUMP: {
                        Block block = mustGetByPc(code >>> 8);
                        addEdge(Edge.Type.JUMP, current, block);
                        break;
                    }
                    case Code.JUMP_IF_FALSE:
                    case Code.JUMP_IF_TRUE: {
                        Block block = mustGetByPc(code >>> 8);
                        addEdge(Edge.Type.JUMP, current, block);
                        addFallthrough(current);
                        break;
                    }
                    default:
                        Instruction.Throw aThrow = getThrow(c);
                        if (aThrow != Instruction.Throw.NOT) {
                            int cpc = Compiled.searchExpHandle(pc, compiled.getExpIns());
                            if (cpc >= 0) {
                                addEdge(Edge.Type.THROW, current, mustGetByPc(cpc));
                            }
                            if (aThrow == Instruction.Throw.MAYBE) {
                                addFallthrough(current);
                            }
                        } else if (c != Code.END_RETURN) {
                            addFallthrough(current);
                        }
                }
            }

            propagateStackSize();

            for (Block block : cfg.blockTreeMap.values()) {
                block.simulate(compiled, cfg);
            }
            return cfg;
        }

        private void propagateStackSize() {
            for (Block block : cfg.blockTreeMap.values()) {
                block.prepareStackShape(compiled);
            }

            Queue<Block> queue = new ArrayDeque<>();
            Block entry = mustGetByPc(0);
            entry.mergeInputStackSize(0);
            queue.add(entry);
            while (!queue.isEmpty()) {
                Block block = queue.poll();
                for (Edge edge : block.successors) {
                    int nextStackSize = edge.type == Edge.Type.THROW ? 0 : block.outputStackSize;
                    if (edge.successor.mergeInputStackSize(nextStackSize)) {
                        queue.add(edge.successor);
                    }
                }
            }

            for (Block block : cfg.blockTreeMap.values()) {
                block.setFallbackInputStackSize();
            }
        }

        private void addFallthrough(Block current) {
            if (current.endPc >= compiled.getCodes().length) {
                return;
            }
            addEdge(Edge.Type.FALLTHROUGH, current, mustGetByPc(current.endPc));
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
