package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class SsaVmClassGenerator implements Opcodes {
    private static final AtomicLong ID = new AtomicLong();
    private static final String CLZ_PREFIX = "io/fiber/net/script/aot/GeneratedSsaVm_";
    private static final String INIT_OPERANDS = "__INIT_OPERANDS__";
    private static final String ABSTRACT_VM = Type.getInternalName(AbstractVm.class);
    private static final String JSON_NODE = Type.getInternalName(JsonNode.class);
    private static final String JSON_NODE_DESC = Type.getDescriptor(JsonNode.class);
    private static final String ARRAY_NODE = Type.getInternalName(ArrayNode.class);
    private static final String BOOLEAN_NODE_DESC = Type.getDescriptor(io.fiber.net.common.json.BooleanNode.class);
    private static final String TEXT_NODE_DESC = Type.getDescriptor(io.fiber.net.common.json.TextNode.class);
    private static final String MISSING_NODE = Type.getInternalName(MissingNode.class);
    private static final String MAYBE_EMITTER_DESC = Type.getDescriptor(Maybe.Emitter.class);
    private static final String CONSTRUCTOR_DESC = '(' + JSON_NODE_DESC + "Ljava/lang/Object;" + MAYBE_EMITTER_DESC + ")V";
    private static final String RUN_EXCEPTIONS[] = new String[]{Type.getInternalName(io.fiber.net.script.ScriptExecException.class)};
    private static final String OBJECT_ARRAY_DESC = "[Ljava/lang/Object;";
    private static final String JSON_NODE_ARRAY_DESC = "[Lio/fiber/net/common/json/JsonNode;";

    private final Compiled compiled;
    private final String internalClassName;
    private byte[] classData;
    private Object[] operands;

    public SsaVmClassGenerator(Compiled compiled) {
        this.compiled = compiled;
        this.internalClassName = CLZ_PREFIX + ID.incrementAndGet();
    }

    public String getGeneratedClzName() {
        return internalClassName;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends AbstractVm> loadAsClass() throws Throwable {
        byte[] bytes = generateClzData();
        Class<?> clazz = AotSsaGeneratedClassLoader.loadClass(internalClassName.replace('/', '.'), bytes);
        Method method = clazz.getDeclaredMethod(INIT_OPERANDS, Object[].class);
        method.invoke(null, new Object[]{operands});
        return (Class<? extends AbstractVm>) clazz;
    }

    public byte[] generateClzData() {
        if (classData == null) {
            SsaGraph graph = buildGraph();
            validateSupported(graph);
            operands = graph.constants().toArray();
            SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
            AsyncPlan asyncPlan = new AsyncPointAnalyzer().analyze(graph, liveness);
            StoragePlan storage = new SsaStoragePlanner().plan(graph, liveness, asyncPlan);
            classData = emitClass(graph, liveness, storage);
        }
        return classData.clone();
    }

    private SsaGraph buildGraph() {
        Cfg cfg = new AotCfgBuilder().build(compiled);
        CfgOptimizer cfgOptimizer = new CfgOptimizer(Arrays.asList(
                new RemoveUnreachableBlocksPass(),
                new BranchSimplificationPass(compiled.getOperands()),
                new BasicBlockMergePass(),
                new RemoveUnreachableBlocksPass()
        ));
        cfg = cfgOptimizer.optimize(cfg);
        for (int i = 0; i < 4; i++) {
            SsaGraph graph = new SsaBuilder().build(cfg, compiled.getVarTableSize(), compiled.getOperands());
            SsaOptimizer.conservative().optimize(graph);
            Cfg threaded = new JumpThreadingPass().run(cfg, graph);
            if (threaded == cfg) {
                return graph;
            }
            cfg = cfgOptimizer.optimize(threaded);
        }
        SsaGraph graph = new SsaBuilder().build(cfg, compiled.getVarTableSize(), compiled.getOperands());
        SsaOptimizer.conservative().optimize(graph);
        return graph;
    }

    private static void validateSupported(SsaGraph graph) {
        if (!graph.cfg().exceptionRegions().isEmpty()) {
            throw new UnsupportedOperationException("ssa jvm generator does not support exception regions yet");
        }
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                Bytecode bytecode = value.bytecode();
                if (bytecode != null && bytecode.isAsync()) {
                    throw new UnsupportedOperationException("ssa jvm generator does not support async state machine yet");
                }
                if (bytecode != null && bytecode.isSpreadCall()) {
                    throw new UnsupportedOperationException("ssa jvm generator does not support spread call yet");
                }
            }
        }
    }

    private byte[] emitClass(SsaGraph graph, SsaLiveness liveness, StoragePlan storage) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, internalClassName, null, ABSTRACT_VM, null);
        writer.visitField(ACC_PRIVATE | ACC_STATIC, "OPERANDS", OBJECT_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "args", JSON_NODE_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "argCnt", "I", null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "spreadArgs", Type.getDescriptor(ArrayNode.class), null, null).visitEnd();
        emitInitOperands(writer);
        emitConstructor(writer);
        emitGetArgVal(writer);
        emitGetArgCnt(writer);
        emitRun(writer, graph, liveness, storage);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void emitInitOperands(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, INIT_OPERANDS, '(' + OBJECT_ARRAY_DESC + ")V",
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(PUTSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitConstructor(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "<init>", CONSTRUCTOR_DESC, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, ABSTRACT_VM, "<init>", CONSTRUCTOR_DESC, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitGetArgVal(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "getArgVal", "(I)" + JSON_NODE_DESC, null, null);
        mv.visitCode();
        Label arrayArgs = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", Type.getDescriptor(ArrayNode.class));
        mv.visitJumpInsn(IFNULL, arrayArgs);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", Type.getDescriptor(ArrayNode.class));
        mv.visitVarInsn(ILOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, ARRAY_NODE, "path", "(I)" + JSON_NODE_DESC, false);
        mv.visitInsn(ARETURN);
        mv.visitLabel(arrayArgs);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "args", JSON_NODE_ARRAY_DESC);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitInsn(AALOAD);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitGetArgCnt(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "getArgCnt", "()I", null, null);
        mv.visitCode();
        Label arrayArgs = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", Type.getDescriptor(ArrayNode.class));
        mv.visitJumpInsn(IFNULL, arrayArgs);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", Type.getDescriptor(ArrayNode.class));
        mv.visitMethodInsn(INVOKEVIRTUAL, ARRAY_NODE, "size", "()I", false);
        mv.visitInsn(IRETURN);
        mv.visitLabel(arrayArgs);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "argCnt", "I");
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitRun(ClassWriter writer, SsaGraph graph, SsaLiveness liveness, StoragePlan storage) {
        MethodVisitor mv = writer.visitMethod(ACC_PROTECTED, "run", "()V", null, RUN_EXCEPTIONS);
        mv.visitCode();
        Map<BasicBlock, Label> labels = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            labels.put(block.basicBlock(), new Label());
        }
        for (int i = 0; i < graph.blocks().size(); i++) {
            SsaBlock block = graph.blocks().get(i);
            mv.visitLabel(labels.get(block.basicBlock()));
            if (i == 0) {
                mv.visitFrame(F_SAME, 0, null, 0, null);
            }
            for (SsaValue value : block.instructions()) {
                emitInstruction(mv, graph, liveness, storage, value, labels);
            }
            emitFallthrough(mv, graph, liveness, storage, block, i, labels);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitInstruction(MethodVisitor mv,
                                 SsaGraph graph,
                                 SsaLiveness liveness,
                                 StoragePlan storage,
                                 SsaValue value,
                                 Map<BasicBlock, Label> labels) {
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case UNDEFINED:
            case PHI:
            case LOAD_VAR:
            case STORE_VAR:
            case DUP:
                emitValue(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case NEW_OBJECT:
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(JsonUtil.class), "createObjectNode",
                        "()" + Type.getDescriptor(io.fiber.net.common.json.ObjectNode.class), false);
                storeValue(mv, storage, value);
                break;
            case NEW_ARRAY:
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(JsonUtil.class), "createArrayNode",
                        "()" + Type.getDescriptor(ArrayNode.class), false);
                storeValue(mv, storage, value);
                break;
            case BINARY:
                emitBinary(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case UNARY:
                emitUnary(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case PROP_GET:
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitLdcInsn((String) graph.constant(value.index()));
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Access.class), "propGet",
                        '(' + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case PROP_SET:
                emitValue(mv, graph, storage, value.operands().get(0));
                emitValue(mv, graph, storage, value.operands().get(1));
                mv.visitLdcInsn((String) graph.constant(value.index()));
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Access.class),
                        value.bytecode().op() == Op.PROP_SET_1 ? "propSet1" : "propSet",
                        '(' + JSON_NODE_DESC + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case INDEX_GET:
                emitValue(mv, graph, storage, value.operands().get(0));
                emitValue(mv, graph, storage, value.operands().get(1));
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Access.class), "indexGet",
                        '(' + JSON_NODE_DESC + JSON_NODE_DESC + ')' + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case INDEX_SET:
                emitValue(mv, graph, storage, value.operands().get(0));
                emitValue(mv, graph, storage, value.operands().get(1));
                emitValue(mv, graph, storage, value.operands().get(2));
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Access.class),
                        value.bytecode().op() == Op.IDX_SET_1 ? "indexSet1" : "indexSet",
                        '(' + JSON_NODE_DESC + JSON_NODE_DESC + JSON_NODE_DESC + ')' + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case EXP_OBJECT:
            case EXP_ARRAY:
            case PUSH_ARRAY:
                emitParentAddition(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case CALL:
                emitFunctionCall(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case CONST_CALL:
                mv.visitFieldInsn(GETSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
                pushInt(mv, value.index());
                mv.visitInsn(AALOAD);
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Library.Constant.class));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Library.Constant.class), "get",
                        "(Lio/fiber/net/script/ExecutionContext;)" + JSON_NODE_DESC, true);
                mv.visitMethodInsn(INVOKESTATIC, ABSTRACT_VM, "nullNode", '(' + JSON_NODE_DESC + ')' + JSON_NODE_DESC,
                        false);
                storeValue(mv, storage, value);
                break;
            case BRANCH:
                emitBranch(mv, graph, liveness, storage, value, labels);
                break;
            case JUMP:
                Edge jumpEdge = normalEdge(value.basicBlock(), EdgeKind.JUMP);
                emitEdgeMoves(mv, graph, liveness, storage, jumpEdge);
                mv.visitJumpInsn(GOTO, labels.get(jumpEdge == null ? graph.cfg().blockAtPc(value.index()) : jumpEdge.to()));
                break;
            case RETURN:
                emitReturn(mv, graph, storage, value);
                break;
            case POP:
            case NOOP:
                break;
            default:
                throw new UnsupportedOperationException("unsupported ssa kind for jvm emit: " + value.kind());
        }
    }

    private void emitValue(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        switch (storage.kind(value)) {
            case LOCAL:
                mv.visitVarInsn(ALOAD, storage.local(value));
                return;
            case FIELD:
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, internalClassName, storage.field(value), JSON_NODE_DESC);
                return;
            case CONST:
                emitConstLike(mv, graph, storage, value);
                return;
            default:
                if (value.kind() == SsaKind.PHI || value.kind() == SsaKind.LOAD_VAR || value.kind() == SsaKind.STORE_VAR
                        || value.kind() == SsaKind.DUP) {
                    emitValue(mv, graph, storage, value.operands().get(0));
                    return;
                }
                throw new UnsupportedOperationException("cannot materialize value " + value + " from " + storage.kind(value));
        }
    }

    private void emitConstLike(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        switch (value.kind()) {
            case CONST: {
                Object constant = graph.constant(value.index());
                mv.visitFieldInsn(GETSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
                pushInt(mv, value.index());
                mv.visitInsn(AALOAD);
                mv.visitTypeInsn(CHECKCAST, JSON_NODE);
                if (constant instanceof JsonNode && ((JsonNode) constant).isContainerNode()) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, JSON_NODE, "deepCopy", "()" + JSON_NODE_DESC, false);
                }
                return;
            }
            case ROOT:
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, internalClassName, "root", JSON_NODE_DESC);
                return;
            case UNDEFINED:
                mv.visitMethodInsn(INVOKESTATIC, MISSING_NODE, "getInstance",
                        "()" + Type.getDescriptor(MissingNode.class), false);
                return;
            default:
                emitValue(mv, graph, storage, value.operands().get(0));
        }
    }

    private void storeValue(MethodVisitor mv, StoragePlan storage, SsaValue value) {
        switch (storage.kind(value)) {
            case LOCAL:
                mv.visitVarInsn(ASTORE, storage.local(value));
                break;
            case FIELD:
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(SWAP);
                mv.visitFieldInsn(PUTFIELD, internalClassName, storage.field(value), JSON_NODE_DESC);
                break;
            case CONST:
            case NONE:
                mv.visitInsn(POP);
                break;
            default:
                break;
        }
    }

    private void emitBinary(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        emitValue(mv, graph, storage, value.operands().get(0));
        emitValue(mv, graph, storage, value.operands().get(1));
        String name;
        switch (value.bytecode().op()) {
            case BOP_PLUS: name = "plus"; break;
            case BOP_MINUS: name = "minus"; break;
            case BOP_MULTIPLY: name = "multiply"; break;
            case BOP_DIVIDE: name = "divide"; break;
            case BOP_MOD: name = "modulo"; break;
            case BOP_MATCH: name = "matches"; break;
            case BOP_LT: name = "lt"; break;
            case BOP_LTE: name = "lte"; break;
            case BOP_GT: name = "gt"; break;
            case BOP_GTE: name = "gte"; break;
            case BOP_EQ: name = "eq"; break;
            case BOP_SEQ: name = "seq"; break;
            case BOP_NE: name = "ne"; break;
            case BOP_SNE: name = "sne"; break;
            case BOP_IN: name = "in"; break;
            default: throw new UnsupportedOperationException("unsupported binary op: " + value.bytecode().op());
        }
        String returnDesc;
        switch (value.bytecode().op()) {
            case BOP_MATCH:
            case BOP_LT:
            case BOP_LTE:
            case BOP_GT:
            case BOP_GTE:
            case BOP_EQ:
            case BOP_SEQ:
            case BOP_NE:
            case BOP_SNE:
            case BOP_IN:
                returnDesc = BOOLEAN_NODE_DESC;
                break;
            default:
                returnDesc = JSON_NODE_DESC;
        }
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Binaries.class), name,
                '(' + JSON_NODE_DESC + JSON_NODE_DESC + ')' + returnDesc, false);
    }

    private void emitUnary(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        emitValue(mv, graph, storage, value.operands().get(0));
        String name;
        String returnDesc;
        switch (value.bytecode().op()) {
            case UNARY_PLUS:
                name = "plus";
                returnDesc = JSON_NODE_DESC;
                break;
            case UNARY_MINUS:
                name = "minus";
                returnDesc = JSON_NODE_DESC;
                break;
            case UNARY_NEG:
                name = "neg";
                returnDesc = BOOLEAN_NODE_DESC;
                break;
            case UNARY_TYPEOF:
                name = "typeof";
                returnDesc = TEXT_NODE_DESC;
                break;
            default: throw new UnsupportedOperationException("unsupported unary op: " + value.bytecode().op());
        }
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Unaries.class), name,
                '(' + JSON_NODE_DESC + ')' + returnDesc, false);
    }

    private void emitParentAddition(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        emitValue(mv, graph, storage, value.operands().get(0));
        emitValue(mv, graph, storage, value.operands().get(1));
        String name;
        switch (value.kind()) {
            case EXP_OBJECT: name = "expandObject"; break;
            case EXP_ARRAY: name = "expandArray"; break;
            case PUSH_ARRAY: name = "pushArray"; break;
            default: throw new IllegalStateException();
        }
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Access.class), name,
                '(' + JSON_NODE_DESC + JSON_NODE_DESC + ')' + JSON_NODE_DESC, false);
    }

    private void emitFunctionCall(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        int argc = value.operands().size();
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, argc);
        mv.visitTypeInsn(ANEWARRAY, JSON_NODE);
        for (int i = 0; i < argc; i++) {
            mv.visitInsn(DUP);
            pushInt(mv, i);
            emitValue(mv, graph, storage, value.operands().get(i));
            mv.visitInsn(AASTORE);
        }
        mv.visitFieldInsn(PUTFIELD, internalClassName, "args", JSON_NODE_ARRAY_DESC);
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, argc);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "argCnt", "I");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "spreadArgs", Type.getDescriptor(ArrayNode.class));

        mv.visitFieldInsn(GETSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
        pushInt(mv, value.index());
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Library.Function.class));
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Library.Function.class), "call",
                "(Lio/fiber/net/script/ExecutionContext;Lio/fiber/net/script/Library$Arguments;)" + JSON_NODE_DESC,
                true);
        mv.visitMethodInsn(INVOKESTATIC, ABSTRACT_VM, "nullNode", '(' + JSON_NODE_DESC + ')' + JSON_NODE_DESC,
                false);
    }

    private void emitBranch(MethodVisitor mv,
                            SsaGraph graph,
                            SsaLiveness liveness,
                            StoragePlan storage,
                            SsaValue value,
                            Map<BasicBlock, Label> labels) {
        emitValue(mv, graph, storage, value.operands().get(0));
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Compares.class), "logic", '(' + JSON_NODE_DESC + ")Z",
                false);
        BasicBlock block = value.basicBlock();
        BasicBlock trueTarget = target(block, EdgeKind.COND_TRUE);
        BasicBlock falseTarget = target(block, EdgeKind.COND_FALSE);
        if (trueTarget == null || falseTarget == null) {
            throw new SsaException("branch without conditional targets: " + block);
        }
        Label falsePath = new Label();
        mv.visitJumpInsn(IFEQ, falsePath);
        emitEdgeMoves(mv, graph, liveness, storage, normalEdge(block, EdgeKind.COND_TRUE));
        mv.visitJumpInsn(GOTO, labels.get(trueTarget));
        mv.visitLabel(falsePath);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        emitEdgeMoves(mv, graph, liveness, storage, normalEdge(block, EdgeKind.COND_FALSE));
        mv.visitJumpInsn(GOTO, labels.get(falseTarget));
    }

    private void emitReturn(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value) {
        mv.visitVarInsn(ALOAD, 0);
        if (value.operands().isEmpty()) {
            mv.visitInsn(ACONST_NULL);
        } else {
            emitValue(mv, graph, storage, value.operands().get(0));
        }
        mv.visitFieldInsn(PUTFIELD, internalClassName, "rtValue", JSON_NODE_DESC);
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, AbstractVm.STAT_END_SEC);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "state", "I");
        mv.visitInsn(RETURN);
    }

    private void emitFallthrough(MethodVisitor mv,
                                 SsaGraph graph,
                                 SsaLiveness liveness,
                                 StoragePlan storage,
                                 SsaBlock block,
                                 int blockIndex,
                                 Map<BasicBlock, Label> labels) {
        Bytecode last = block.basicBlock().lastCode();
        if (hasEmittedControl(block)) {
            return;
        }
        BasicBlock next = blockIndex + 1 < graph.blocks().size() ? graph.blocks().get(blockIndex + 1).basicBlock() : null;
        for (Edge edge : block.basicBlock().successors()) {
            if (edge.kind() == EdgeKind.EXCEPTION) {
                continue;
            }
            for (SsaLiveness.PhiMove move : liveness.edgeMoves(edge)) {
                emitMove(mv, graph, storage, move);
            }
            if (edge.to() != next) {
                mv.visitJumpInsn(GOTO, labels.get(edge.to()));
            }
            return;
        }
    }

    private static boolean hasEmittedControl(SsaBlock block) {
        for (SsaValue value : block.instructions()) {
            switch (value.kind()) {
                case BRANCH:
                case JUMP:
                case RETURN:
                case THROW:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private static BasicBlock target(BasicBlock block, EdgeKind kind) {
        for (Edge edge : block.successors()) {
            if (edge.kind() == kind) {
                return edge.to();
            }
        }
        return null;
    }

    private void emitEdgeMoves(MethodVisitor mv,
                               SsaGraph graph,
                               SsaLiveness liveness,
                               StoragePlan storage,
                               Edge edge) {
        if (edge == null) {
            return;
        }
        for (SsaLiveness.PhiMove move : liveness.edgeMoves(edge)) {
            emitMove(mv, graph, storage, move);
        }
    }

    private void emitMove(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaLiveness.PhiMove move) {
        emitValue(mv, graph, storage, move.from());
        storeValue(mv, storage, move.to());
    }

    private static Edge normalEdge(BasicBlock block, EdgeKind kind) {
        for (Edge edge : block.successors()) {
            if (edge.kind() == kind) {
                return edge;
            }
        }
        return null;
    }

    private static void pushInt(MethodVisitor mv, int value) {
        switch (value) {
            case -1: mv.visitInsn(ICONST_M1); break;
            case 0: mv.visitInsn(ICONST_0); break;
            case 1: mv.visitInsn(ICONST_1); break;
            case 2: mv.visitInsn(ICONST_2); break;
            case 3: mv.visitInsn(ICONST_3); break;
            case 4: mv.visitInsn(ICONST_4); break;
            case 5: mv.visitInsn(ICONST_5); break;
            default:
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    mv.visitIntInsn(BIPUSH, value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    mv.visitIntInsn(SIPUSH, value);
                } else {
                    mv.visitLdcInsn(value);
                }
        }
    }
}
