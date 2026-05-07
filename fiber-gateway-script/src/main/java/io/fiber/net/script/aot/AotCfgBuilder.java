package io.fiber.net.script.aot;

import io.fiber.net.script.parse.Compiled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public final class AotCfgBuilder {

    public Cfg build(Compiled compiled) {
        return build(Bytecode.decodeAll(compiled.getCodes()), compiled.getExceptionTable());
    }

    public Cfg build(int[] codes, int[] exceptionTable) {
        return build(Bytecode.decodeAll(codes), exceptionTable);
    }

    public Cfg build(Bytecode[] inputBytecodes, int[] exceptionTable) {
        Bytecode[] bytecodes = normalizeBytecodes(inputBytecodes);
        int codeLength = bytecodes.length;
        if (codeLength == 0) {
            throw new CfgException("cannot build cfg for empty bytecode");
        }

        TreeSet<Integer> leaders = collectLeaders(bytecodes, exceptionTable);
        List<BasicBlock> blocks = createBlocks(bytecodes, leaders);
        BasicBlock[] blockByPc = createPcIndex(codeLength, blocks);
        addNormalEdges(bytecodes, blocks, blockByPc);
        List<ExceptionRegion> regions = createExceptionRegions(exceptionTable, codeLength, blockByPc);
        addExceptionEdges(blocks, regions);

        return new Cfg(bytecodes, blocks, blockByPc, regions);
    }

    private static Bytecode[] normalizeBytecodes(Bytecode[] inputBytecodes) {
        if (inputBytecodes == null) {
            throw new CfgException("bytecodes is null");
        }
        Bytecode[] bytecodes = new Bytecode[inputBytecodes.length];
        for (int i = 0; i < inputBytecodes.length; i++) {
            Bytecode bytecode = inputBytecodes[i];
            if (bytecode == null) {
                throw new CfgException("bytecode is null at pc " + i);
            }
            bytecodes[i] = bytecode.withPc(i);
        }
        return bytecodes;
    }

    private static TreeSet<Integer> collectLeaders(Bytecode[] bytecodes, int[] exceptionTable) {
        int codeLength = bytecodes.length;
        TreeSet<Integer> leaders = new TreeSet<>();
        leaders.add(0);

        for (Bytecode bytecode : bytecodes) {
            Op op = bytecode.op();
            int nextPc = bytecode.pc() + 1;
            if (bytecode.isJump()) {
                int targetPc = bytecode.targetPc();
                validateTargetPc(targetPc, codeLength, "jump target", bytecode.pc());
                leaders.add(targetPc);
                if (bytecode.isConditionalJump()) {
                    validateTargetPc(nextPc, codeLength, "conditional fallthrough", bytecode.pc());
                    leaders.add(nextPc);
                } else if (nextPc < codeLength) {
                    leaders.add(nextPc);
                }
            } else if (op.isTerminal() && nextPc < codeLength) {
                leaders.add(nextPc);
            }
        }

        if (exceptionTable != null) {
            validateExceptionTableShape(exceptionTable);
            for (int i = 0; i < exceptionTable.length; i += 3) {
                int tryBegin = exceptionTable[i];
                int catchBegin = exceptionTable[i + 1];
                int catchEnd = exceptionTable[i + 2];
                validateExceptionRange(tryBegin, catchBegin, catchEnd, codeLength);
                leaders.add(tryBegin);
                leaders.add(catchBegin);
                if (catchEnd < codeLength) {
                    leaders.add(catchEnd);
                }
            }
        }

        return leaders;
    }

    private static List<BasicBlock> createBlocks(Bytecode[] bytecodes, TreeSet<Integer> leaders) {
        int codeLength = bytecodes.length;
        List<Integer> starts = new ArrayList<>(leaders);
        Collections.sort(starts);

        List<BasicBlock> blocks = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int startPc = starts.get(i);
            validateTargetPc(startPc, codeLength, "block leader", startPc);
            int endPc = i + 1 < starts.size() ? starts.get(i + 1) : codeLength;
            if (startPc >= endPc) {
                throw new CfgException("invalid block range [" + startPc + ',' + endPc + ')');
            }
            Bytecode[] codes = Arrays.copyOfRange(bytecodes, startPc, endPc);
            blocks.add(new BasicBlock(i, startPc, endPc, codes));
        }
        return blocks;
    }

    private static BasicBlock[] createPcIndex(int codeLength, List<BasicBlock> blocks) {
        BasicBlock[] blockByPc = new BasicBlock[codeLength];
        for (BasicBlock block : blocks) {
            for (int pc = block.startPc(); pc < block.endPc(); pc++) {
                blockByPc[pc] = block;
            }
        }
        return blockByPc;
    }

    private static void addNormalEdges(Bytecode[] bytecodes, List<BasicBlock> blocks, BasicBlock[] blockByPc) {
        int codeLength = bytecodes.length;
        for (BasicBlock block : blocks) {
            Bytecode last = block.lastCode();
            Op op = last.op();
            if (op == Op.JUMP) {
                block.addEdgeTo(requiredBlockAt(blockByPc, last.targetPc(), "jump target", last.pc()), EdgeKind.JUMP);
            } else if (op == Op.JUMP_IF_FALSE || op == Op.JUMP_IF_TRUE) {
                BasicBlock target = requiredBlockAt(blockByPc, last.targetPc(), "conditional target", last.pc());
                BasicBlock fallthrough = requiredBlockAt(blockByPc, last.pc() + 1, "conditional fallthrough", last.pc());
                if (op == Op.JUMP_IF_TRUE) {
                    block.addEdgeTo(target, EdgeKind.COND_TRUE);
                    block.addEdgeTo(fallthrough, EdgeKind.COND_FALSE);
                } else {
                    block.addEdgeTo(target, EdgeKind.COND_FALSE);
                    block.addEdgeTo(fallthrough, EdgeKind.COND_TRUE);
                }
            } else if (!op.isTerminal() && block.endPc() < codeLength) {
                block.addEdgeTo(requiredBlockAt(blockByPc, block.endPc(), "fallthrough", last.pc()), EdgeKind.FALLTHROUGH);
            }
        }
    }

    private static List<ExceptionRegion> createExceptionRegions(int[] exceptionTable,
                                                                int codeLength,
                                                                BasicBlock[] blockByPc) {
        if (exceptionTable == null || exceptionTable.length == 0) {
            return Collections.emptyList();
        }
        validateExceptionTableShape(exceptionTable);
        List<ExceptionRegion> regions = new ArrayList<>(exceptionTable.length / 3);
        for (int i = 0; i < exceptionTable.length; i += 3) {
            int tryBegin = exceptionTable[i];
            int catchBegin = exceptionTable[i + 1];
            int catchEnd = exceptionTable[i + 2];
            validateExceptionRange(tryBegin, catchBegin, catchEnd, codeLength);
            BasicBlock catchBlock = requiredBlockAt(blockByPc, catchBegin, "catch begin", catchBegin);
            regions.add(new ExceptionRegion(tryBegin, catchBegin, catchBegin, catchEnd, catchBlock));
        }
        return regions;
    }

    private static void addExceptionEdges(List<BasicBlock> blocks, List<ExceptionRegion> regions) {
        if (regions.isEmpty()) {
            return;
        }
        for (ExceptionRegion region : regions) {
            for (BasicBlock block : blocks) {
                if (block.startPc() >= region.tryEndPc() || block.endPc() <= region.tryStartPc()) {
                    continue;
                }
                if (mayThrowInRegion(block, region)) {
                    block.addEdgeTo(region.catchBlock(), EdgeKind.EXCEPTION);
                }
            }
        }
    }

    private static boolean mayThrowInRegion(BasicBlock block, ExceptionRegion region) {
        for (int i = 0; i < block.codeLength(); i++) {
            Bytecode bytecode = block.codeAt(i);
            if (region.containsTryPc(bytecode.pc()) && bytecode.mayThrow()) {
                return true;
            }
        }
        return false;
    }

    private static BasicBlock requiredBlockAt(BasicBlock[] blockByPc, int pc, String kind, int sourcePc) {
        if (pc < 0 || pc >= blockByPc.length) {
            throw new CfgException(kind + " out of range at pc " + sourcePc + ": " + pc);
        }
        BasicBlock block = blockByPc[pc];
        if (block == null) {
            throw new CfgException(kind + " does not map to a block at pc " + sourcePc + ": " + pc);
        }
        return block;
    }

    private static void validateTargetPc(int pc, int codeLength, String kind, int sourcePc) {
        if (pc < 0 || pc >= codeLength) {
            throw new CfgException(kind + " out of range at pc " + sourcePc + ": " + pc);
        }
    }

    private static void validateExceptionTableShape(int[] exceptionTable) {
        if ((exceptionTable.length % 3) != 0) {
            throw new CfgException("exception table length must be a multiple of 3: " + exceptionTable.length);
        }
    }

    private static void validateExceptionRange(int tryBegin, int catchBegin, int catchEnd, int codeLength) {
        if (tryBegin < 0 || catchBegin < 0 || catchEnd < 0 || catchEnd > codeLength) {
            throw new CfgException("exception range out of bounds: [" + tryBegin + ',' + catchBegin + ',' + catchEnd
                    + "], codeLength=" + codeLength);
        }
        if (!(tryBegin < catchBegin && catchBegin < catchEnd)) {
            throw new CfgException("invalid exception range: [" + tryBegin + ',' + catchBegin + ',' + catchEnd + ']');
        }
    }
}
