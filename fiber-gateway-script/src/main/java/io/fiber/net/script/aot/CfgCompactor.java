package io.fiber.net.script.aot;

import io.fiber.net.script.run.Code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CfgCompactor {
    public CompactBytecode compact(Cfg cfg) {
        Layout layout = layout(cfg);
        ArrayList<Integer> codes = new ArrayList<>(layout.codeLength);
        HashMap<Integer, Integer> oldPcToNewPc = new HashMap<>();
        for (int i = 0; i < cfg.blocks().size(); i++) {
            emitBlock(cfg, cfg.blocks().get(i), nextBlock(cfg, i), layout.blockStartPc, codes, oldPcToNewPc);
        }
        int[] compactCodes = toArray(codes);
        int[] exceptionTable = compactExceptionTable(cfg, layout.blockStartPc, layout.blockEndPc);
        return new CompactBytecode(compactCodes, exceptionTable, oldPcToNewPc, layout.blockStartPc);
    }

    private static Layout layout(Cfg cfg) {
        IdentityHashMap<BasicBlock, Integer> starts = new IdentityHashMap<>();
        IdentityHashMap<BasicBlock, Integer> ends = new IdentityHashMap<>();
        int pc = 0;
        for (int i = 0; i < cfg.blocks().size(); i++) {
            BasicBlock block = cfg.blocks().get(i);
            starts.put(block, pc);
            pc += emittedLength(block, nextBlock(cfg, i));
            ends.put(block, pc);
        }
        return new Layout(starts, ends, pc);
    }

    private static int emittedLength(BasicBlock block, BasicBlock nextBlock) {
        List<Edge> normal = normalSuccessors(block);
        Bytecode last = block.lastCode();
        if (normal.isEmpty()) {
            return last.op().isTerminal() ? block.codeLength() : block.codeLength() + 1;
        }
        if (normal.size() == 1) {
            BasicBlock target = normal.get(0).to();
            int length = nonBranchBodyLength(block);
            if (last.op().isConditionalJump()) {
                length++;
            }
            if (target != nextBlock) {
                length++;
            }
            return length;
        }
        if (normal.size() == 2) {
            BasicBlock trueTarget = target(block, EdgeKind.COND_TRUE);
            BasicBlock falseTarget = target(block, EdgeKind.COND_FALSE);
            if (trueTarget == null || falseTarget == null) {
                throw new CfgException("conditional block has invalid successors: " + block);
            }
            int length = block.codeLength() - 1;
            return trueTarget == nextBlock || falseTarget == nextBlock ? length + 1 : length + 2;
        }
        throw new CfgException("block has too many normal successors: " + block);
    }

    private static void emitBlock(Cfg cfg,
                                  BasicBlock block,
                                  BasicBlock nextBlock,
                                  IdentityHashMap<BasicBlock, Integer> blockStartPc,
                                  ArrayList<Integer> out,
                                  HashMap<Integer, Integer> oldPcToNewPc) {
        List<Edge> normal = normalSuccessors(block);
        Bytecode last = block.lastCode();
        if (normal.isEmpty()) {
            emitBody(block, last.op().isTerminal() ? block.codeLength() : block.codeLength(), out, oldPcToNewPc);
            if (!last.op().isTerminal()) {
                out.add(Code.END_RETURN);
            }
            return;
        }
        if (normal.size() == 1) {
            emitSingleSuccessor(block, normal.get(0).to(), nextBlock, blockStartPc, out, oldPcToNewPc);
            return;
        }
        if (normal.size() == 2) {
            emitConditional(block, nextBlock, blockStartPc, out, oldPcToNewPc);
            return;
        }
        throw new CfgException("block has too many normal successors: " + block);
    }

    private static void emitSingleSuccessor(BasicBlock block,
                                            BasicBlock target,
                                            BasicBlock nextBlock,
                                            IdentityHashMap<BasicBlock, Integer> blockStartPc,
                                            ArrayList<Integer> out,
                                            HashMap<Integer, Integer> oldPcToNewPc) {
        Bytecode last = block.lastCode();
        int bodyLength = nonBranchBodyLength(block);
        emitBody(block, bodyLength, out, oldPcToNewPc);
        if (last.op().isConditionalJump()) {
            out.add(Code.POP);
        }
        if (target != nextBlock) {
            out.add(jumpRaw(Op.JUMP, requiredStart(blockStartPc, target)));
        }
    }

    private static void emitConditional(BasicBlock block,
                                        BasicBlock nextBlock,
                                        IdentityHashMap<BasicBlock, Integer> blockStartPc,
                                        ArrayList<Integer> out,
                                        HashMap<Integer, Integer> oldPcToNewPc) {
        Bytecode last = block.lastCode();
        if (!last.op().isConditionalJump()) {
            throw new CfgException("conditional successors require conditional terminator: " + block);
        }
        BasicBlock trueTarget = target(block, EdgeKind.COND_TRUE);
        BasicBlock falseTarget = target(block, EdgeKind.COND_FALSE);
        emitBody(block, block.codeLength() - 1, out, oldPcToNewPc);
        if (trueTarget == nextBlock) {
            out.add(jumpRaw(Op.JUMP_IF_FALSE, requiredStart(blockStartPc, falseTarget)));
        } else if (falseTarget == nextBlock) {
            out.add(jumpRaw(Op.JUMP_IF_TRUE, requiredStart(blockStartPc, trueTarget)));
        } else {
            out.add(jumpRaw(Op.JUMP_IF_FALSE, requiredStart(blockStartPc, falseTarget)));
            out.add(jumpRaw(Op.JUMP, requiredStart(blockStartPc, trueTarget)));
        }
    }

    private static void emitBody(BasicBlock block,
                                 int length,
                                 ArrayList<Integer> out,
                                 HashMap<Integer, Integer> oldPcToNewPc) {
        for (int i = 0; i < length; i++) {
            Bytecode bytecode = block.codeAt(i);
            oldPcToNewPc.put(bytecode.pc(), out.size());
            out.add(bytecode.raw());
        }
    }

    private static int nonBranchBodyLength(BasicBlock block) {
        Bytecode last = block.lastCode();
        return last.op().isJump() || last.op().isConditionalJump() ? block.codeLength() - 1 : block.codeLength();
    }

    private static List<Edge> normalSuccessors(BasicBlock block) {
        ArrayList<Edge> normal = new ArrayList<>(block.successors().size());
        for (Edge edge : block.successors()) {
            if (edge.kind() != EdgeKind.EXCEPTION) {
                normal.add(edge);
            }
        }
        return normal;
    }

    private static BasicBlock target(BasicBlock block, EdgeKind kind) {
        for (Edge edge : block.successors()) {
            if (edge.kind() == kind) {
                return edge.to();
            }
        }
        return null;
    }

    private static BasicBlock nextBlock(Cfg cfg, int index) {
        return index + 1 < cfg.blocks().size() ? cfg.blocks().get(index + 1) : null;
    }

    private static int requiredStart(IdentityHashMap<BasicBlock, Integer> blockStartPc, BasicBlock block) {
        Integer start = blockStartPc.get(block);
        if (start == null) {
            throw new CfgException("missing compact pc for block " + block);
        }
        return start;
    }

    private static int jumpRaw(Op op, int targetPc) {
        return (targetPc << 8) | op.code();
    }

    private static int[] compactExceptionTable(Cfg cfg,
                                               IdentityHashMap<BasicBlock, Integer> blockStartPc,
                                               IdentityHashMap<BasicBlock, Integer> blockEndPc) {
        if (cfg.exceptionRegions().isEmpty()) {
            return new int[0];
        }
        ArrayList<Integer> table = new ArrayList<>(cfg.exceptionRegions().size() * 3);
        for (ExceptionRegion region : cfg.exceptionRegions()) {
            Range tryRange = rangeFor(cfg, region.tryStartPc(), region.tryEndPc(), blockStartPc, blockEndPc);
            Range catchRange = rangeFor(cfg, region.catchStartPc(), region.catchEndPc(), blockStartPc, blockEndPc);
            Integer catchStart = blockStartPc.get(region.catchBlock());
            if (tryRange == null || catchRange == null || catchStart == null) {
                continue;
            }
            if (tryRange.start >= catchStart || catchStart >= catchRange.end) {
                throw new CfgException("cannot compact non-contiguous exception region: " + region);
            }
            table.add(tryRange.start);
            table.add(catchStart);
            table.add(catchRange.end);
        }
        return toArray(table);
    }

    private static Range rangeFor(Cfg cfg,
                                  int oldStart,
                                  int oldEnd,
                                  IdentityHashMap<BasicBlock, Integer> blockStartPc,
                                  IdentityHashMap<BasicBlock, Integer> blockEndPc) {
        int start = Integer.MAX_VALUE;
        int end = -1;
        for (BasicBlock block : cfg.blocks()) {
            if (block.startPc() >= oldEnd || block.endPc() <= oldStart || !blockStartPc.containsKey(block)) {
                continue;
            }
            start = Math.min(start, blockStartPc.get(block));
            end = Math.max(end, blockEndPc.get(block));
        }
        return end < 0 ? null : new Range(start, end);
    }

    private static int[] toArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static final class Layout {
        private final IdentityHashMap<BasicBlock, Integer> blockStartPc;
        private final IdentityHashMap<BasicBlock, Integer> blockEndPc;
        private final int codeLength;

        private Layout(IdentityHashMap<BasicBlock, Integer> blockStartPc,
                       IdentityHashMap<BasicBlock, Integer> blockEndPc,
                       int codeLength) {
            this.blockStartPc = blockStartPc;
            this.blockEndPc = blockEndPc;
            this.codeLength = codeLength;
        }
    }

    private static final class Range {
        private final int start;
        private final int end;

        private Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
