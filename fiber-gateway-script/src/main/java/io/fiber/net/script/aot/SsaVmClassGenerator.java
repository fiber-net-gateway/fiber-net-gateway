package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IteratorNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
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
import java.util.List;
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
    private static final String ARRAY_NODE_DESC = Type.getDescriptor(ArrayNode.class);
    private static final String ITERATOR_NODE = Type.getInternalName(IteratorNode.class);
    private static final String ITERATOR_NODE_DESC = Type.getDescriptor(IteratorNode.class);
    private static final String BOOLEAN_NODE_DESC = Type.getDescriptor(io.fiber.net.common.json.BooleanNode.class);
    private static final String TEXT_NODE_DESC = Type.getDescriptor(io.fiber.net.common.json.TextNode.class);
    private static final String MISSING_NODE = Type.getInternalName(MissingNode.class);
    private static final String SCRIPT_EXEC_EXCEPTION = Type.getInternalName(ScriptExecException.class);
    private static final String SCRIPT_EXEC_EXCEPTION_DESC = Type.getDescriptor(ScriptExecException.class);
    private static final String MAYBE_EMITTER_DESC = Type.getDescriptor(Maybe.Emitter.class);
    private static final String CONSTRUCTOR_DESC = '(' + JSON_NODE_DESC + "Ljava/lang/Object;" + MAYBE_EMITTER_DESC + ")V";
    private static final String RUN_EXCEPTIONS[] = new String[]{SCRIPT_EXEC_EXCEPTION};
    private static final String OBJECT_ARRAY_DESC = "[Ljava/lang/Object;";
    private static final String JSON_NODE_ARRAY_DESC = "[Lio/fiber/net/common/json/JsonNode;";
    private static final String LONG_ARRAY_DESC = "[J";

    private final Compiled compiled;
    private final String internalClassName;
    private byte[] classData;
    private Object[] operands;
    private long[] positions;

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
        Method method = clazz.getDeclaredMethod(INIT_OPERANDS, Object[].class, long[].class);
        method.invoke(null, operands, positions);
        return (Class<? extends AbstractVm>) clazz;
    }

    public byte[] generateClzData() {
        if (classData == null) {
            SsaGraph graph = buildGraph();
            operands = graph.constants().toArray();
            positions = compiled.getPos();
            SsaLiveness liveness = new SsaLivenessAnalyzer().analyze(graph);
            AsyncPlan asyncPlan = new AsyncPointAnalyzer().analyze(graph, liveness);
            StoragePlan storage = new SsaStoragePlanner().plan(graph, liveness, asyncPlan);
            classData = emitClass(graph, liveness, asyncPlan, storage);
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

    private byte[] emitClass(SsaGraph graph, SsaLiveness liveness, AsyncPlan asyncPlan, StoragePlan storage) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, internalClassName, null, ABSTRACT_VM, null);
        writer.visitField(ACC_PRIVATE | ACC_STATIC, "OPERANDS", OBJECT_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_STATIC, "POS", LONG_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "args", JSON_NODE_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "argCnt", "I", null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "spreadArgs", ARRAY_NODE_DESC, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "asyncState", "I", null, null).visitEnd();
        writer.visitField(ACC_PRIVATE, "currentPc", "I", null, null).visitEnd();
        for (Map.Entry<SsaValue, String> entry : storage.fields().entrySet()) {
            writer.visitField(ACC_PRIVATE, entry.getValue(), valueDesc(entry.getKey()), null, null).visitEnd();
        }
        emitInitOperands(writer);
        emitConstructor(writer);
        emitGetArgVal(writer);
        emitGetArgCnt(writer);
        emitGetCurrentPc(writer);
        emitRun(writer, graph, liveness, asyncPlan, storage);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void emitInitOperands(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, INIT_OPERANDS, '(' + OBJECT_ARRAY_DESC + LONG_ARRAY_DESC + ")V",
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(PUTSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTSTATIC, internalClassName, "POS", LONG_ARRAY_DESC);
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
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
        mv.visitJumpInsn(IFNULL, arrayArgs);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
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
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
        mv.visitJumpInsn(IFNULL, arrayArgs);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
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

    private void emitGetCurrentPc(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "getCurrentPc", "()I", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "currentPc", "I");
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitAsyncSwitch(MethodVisitor mv, AsyncPlan asyncPlan, Map<SsaValue, Label> asyncLabels) {
        if (asyncPlan.points().isEmpty()) {
            return;
        }
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "asyncState", "I");
        Label start = new Label();
        Label defaultLabel = new Label();
        Label[] labels = new Label[asyncPlan.points().size() + 1];
        labels[0] = start;
        for (AsyncPlan.AsyncPoint point : asyncPlan.points().values()) {
            labels[point.resumeId()] = asyncLabels.get(point.value());
        }
        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        mv.visitLabel(defaultLabel);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("[BUG] invalid asyncState");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitLabel(start);
        mv.visitFrame(F_SAME, 0, null, 0, null);
    }

    private void emitRun(ClassWriter writer, SsaGraph graph, SsaLiveness liveness, AsyncPlan asyncPlan, StoragePlan storage) {
        MethodVisitor mv = writer.visitMethod(ACC_PROTECTED, "run", "()V", null, RUN_EXCEPTIONS);
        mv.visitCode();
        Map<BasicBlock, Label> labels = new IdentityHashMap<>();
        for (SsaBlock block : graph.blocks()) {
            labels.put(block.basicBlock(), new Label());
        }
        Map<SsaValue, Label> asyncLabels = new IdentityHashMap<>();
        for (AsyncPlan.AsyncPoint point : asyncPlan.points().values()) {
            asyncLabels.put(point.value(), new Label());
        }
        Map<SsaValue, Label> protectedStarts = new IdentityHashMap<>();
        Map<SsaValue, Label> protectedEnds = new IdentityHashMap<>();
        Map<SsaExceptionInput, Label> exceptionHandlers = new IdentityHashMap<>();
        collectExceptionHandlerLabels(graph, protectedStarts, protectedEnds, exceptionHandlers);
        Label methodStart = new Label();
        Label methodEnd = new Label();
        Label generalHandler = new Label();
        emitAsyncSwitch(mv, asyncPlan, asyncLabels);
        mv.visitLabel(methodStart);
        emitInitJsonLocals(mv, storage);
        for (int i = 0; i < graph.blocks().size(); i++) {
            SsaBlock block = graph.blocks().get(i);
            mv.visitLabel(labels.get(block.basicBlock()));
            if (i == 0) {
                mv.visitFrame(F_SAME, 0, null, 0, null);
            }
            for (SsaValue value : block.instructions()) {
                Label protectedStart = protectedStarts.get(value);
                if (protectedStart != null) {
                    mv.visitLabel(protectedStart);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                }
                emitInstruction(mv, graph, liveness, asyncPlan, storage, value, labels, asyncLabels);
                Label protectedEnd = protectedEnds.get(value);
                if (protectedEnd != null) {
                    mv.visitLabel(protectedEnd);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                }
            }
            emitFallthrough(mv, graph, liveness, storage, block, i, labels);
        }
        mv.visitLabel(methodEnd);
        mv.visitInsn(RETURN);
        emitExceptionHandlers(mv, graph, storage, labels, protectedStarts, protectedEnds, exceptionHandlers,
                generalHandler, methodStart, methodEnd);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void collectExceptionHandlerLabels(SsaGraph graph,
                                                      Map<SsaValue, Label> protectedStarts,
                                                      Map<SsaValue, Label> protectedEnds,
                                                      Map<SsaExceptionInput, Label> exceptionHandlers) {
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (value.bytecode() == null) {
                    continue;
                }
                List<SsaExceptionInput> inputs = block.exceptionInputsAt(value.bytecode().pc() - block.basicBlock().startPc());
                if (inputs.isEmpty()) {
                    continue;
                }
                protectedStarts.put(value, new Label());
                protectedEnds.put(value, new Label());
                for (SsaExceptionInput input : inputs) {
                    exceptionHandlers.put(input, new Label());
                }
            }
        }
    }

    private void emitExceptionHandlers(MethodVisitor mv,
                                       SsaGraph graph,
                                       StoragePlan storage,
                                       Map<BasicBlock, Label> labels,
                                       Map<SsaValue, Label> protectedStarts,
                                       Map<SsaValue, Label> protectedEnds,
                                       Map<SsaExceptionInput, Label> exceptionHandlers,
                                       Label generalHandler,
                                       Label methodStart,
                                       Label methodEnd) {
        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (value.bytecode() == null) {
                    continue;
                }
                List<SsaExceptionInput> inputs = block.exceptionInputsAt(value.bytecode().pc() - block.basicBlock().startPc());
                for (SsaExceptionInput input : inputs) {
                    Label handler = exceptionHandlers.get(input);
                    mv.visitLabel(handler);
                    mv.visitFrame(F_SAME1, 0, null, 1, new Object[]{SCRIPT_EXEC_EXCEPTION});
                    emitExceptionInputHandler(mv, graph, storage, labels, input);
                }
            }
        }
        mv.visitLabel(generalHandler);
        mv.visitFrame(F_SAME1, 0, null, 1, new Object[]{SCRIPT_EXEC_EXCEPTION});
        int local = 1;
        mv.visitVarInsn(ASTORE, local);
        mv.visitVarInsn(ALOAD, local);
        emitSetPosFromCurrentPcOnStack(mv);
        mv.visitVarInsn(ALOAD, local);
        mv.visitInsn(ATHROW);

        for (SsaBlock block : graph.blocks()) {
            for (SsaValue value : block.instructions()) {
                if (value.bytecode() == null) {
                    continue;
                }
                List<SsaExceptionInput> inputs = block.exceptionInputsAt(value.bytecode().pc() - block.basicBlock().startPc());
                for (SsaExceptionInput input : inputs) {
                    mv.visitTryCatchBlock(protectedStarts.get(value), protectedEnds.get(value),
                            exceptionHandlers.get(input), SCRIPT_EXEC_EXCEPTION);
                }
            }
        }
        mv.visitTryCatchBlock(methodStart, methodEnd, generalHandler, SCRIPT_EXEC_EXCEPTION);
    }

    private void emitExceptionInputHandler(MethodVisitor mv,
                                           SsaGraph graph,
                                           StoragePlan storage,
                                           Map<BasicBlock, Label> labels,
                                           SsaExceptionInput input) {
        SsaBlock catchBlock = input.catchBlock();
        SsaValue exceptionValue = catchBlock.exceptionValue();
        mv.visitInsn(DUP);
        storeRawValue(mv, storage, exceptionValue);
        emitSetPosFromCurrentPcOnStack(mv);
        VarState incoming = input.vars();
        VarState target = catchBlock.inVars();
        for (int i = 0; i < target.size(); i++) {
            SsaValue to = target.get(i);
            SsaValue from = incoming.get(i);
            if (to != from) {
                emitValue(mv, graph, storage, from);
                storeValue(mv, storage, to);
            }
        }
        mv.visitJumpInsn(GOTO, labels.get(catchBlock.basicBlock()));
    }

    private void emitInitJsonLocals(MethodVisitor mv, StoragePlan storage) {
        for (Map.Entry<SsaValue, Integer> entry : storage.locals().entrySet()) {
            if (!JSON_NODE_DESC.equals(valueDesc(entry.getKey()))) {
                continue;
            }
            mv.visitMethodInsn(INVOKESTATIC, MISSING_NODE, "getInstance",
                    "()" + Type.getDescriptor(MissingNode.class), false);
            mv.visitVarInsn(ASTORE, entry.getValue());
        }
    }

    private void emitInstruction(MethodVisitor mv,
                                 SsaGraph graph,
                                 SsaLiveness liveness,
                                 AsyncPlan asyncPlan,
                                 StoragePlan storage,
                                 SsaValue value,
                                 Map<BasicBlock, Label> labels,
                                 Map<SsaValue, Label> asyncLabels) {
        setCurrentPc(mv, value.bytecode());
        switch (value.kind()) {
            case CONST:
            case ROOT:
            case UNDEFINED:
                emitConstLike(mv, graph, storage, value);
                storeValue(mv, storage, value);
                break;
            case LOAD_VAR:
            case STORE_VAR:
            case DUP:
                emitValue(mv, graph, storage, value.operands().get(0));
                storeValue(mv, storage, value);
                break;
            case PHI:
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
            case ASYNC_CALL:
                emitAsyncFunctionCall(mv, graph, asyncPlan, storage, value, asyncLabels);
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
            case ASYNC_CONST_CALL:
                emitAsyncConstCall(mv, asyncPlan, storage, value, asyncLabels);
                storeValue(mv, storage, value);
                break;
            case ITERATE_INTO:
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Unaries.class), "iterate",
                        '(' + JSON_NODE_DESC + ')' + ITERATOR_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case ITERATE_NEXT:
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitTypeInsn(CHECKCAST, ITERATOR_NODE);
                mv.visitMethodInsn(INVOKEVIRTUAL, ITERATOR_NODE, "next", "()Z", false);
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BooleanNode.class), "valueOf",
                        "(Z)" + BOOLEAN_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case ITERATE_VAR:
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitTypeInsn(CHECKCAST, ITERATOR_NODE);
                mv.visitMethodInsn(INVOKEVIRTUAL, ITERATOR_NODE,
                        value.bytecode().op() == Op.ITERATE_KEY ? "currentKey" : "currentValue",
                        "()" + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case ERROR_TO_OBJ:
                mv.visitVarInsn(ALOAD, 0);
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitTypeInsn(CHECKCAST, SCRIPT_EXEC_EXCEPTION);
                mv.visitFieldInsn(PUTFIELD, internalClassName, "rtError", SCRIPT_EXEC_EXCEPTION_DESC);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, internalClassName, "errorToObj", "()" + JSON_NODE_DESC, false);
                storeValue(mv, storage, value);
                break;
            case THROW:
                mv.visitVarInsn(ALOAD, 0);
                emitValue(mv, graph, storage, value.operands().get(0));
                mv.visitMethodInsn(INVOKEVIRTUAL, internalClassName, "objToError",
                        '(' + JSON_NODE_DESC + ')' + SCRIPT_EXEC_EXCEPTION_DESC, false);
                mv.visitInsn(ATHROW);
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
        if ((value.kind() == SsaKind.LOAD_VAR || value.kind() == SsaKind.STORE_VAR || value.kind() == SsaKind.DUP)
                && !value.operands().isEmpty()) {
            emitValue(mv, graph, storage, value.operands().get(0));
            return;
        }
        switch (storage.kind(value)) {
            case LOCAL:
                mv.visitVarInsn(ALOAD, storage.local(value));
                return;
            case FIELD:
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, internalClassName, storage.field(value), valueDesc(value));
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
                if (value.kind() == SsaKind.EXCEPTION) {
                    mv.visitVarInsn(ALOAD, storage.local(value));
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
                mv.visitFieldInsn(PUTFIELD, internalClassName, storage.field(value), valueDesc(value));
                break;
            case CONST:
            case NONE:
                mv.visitInsn(POP);
                break;
            default:
                break;
        }
    }

    private void storeRawValue(MethodVisitor mv, StoragePlan storage, SsaValue value) {
        switch (storage.kind(value)) {
            case LOCAL:
                mv.visitVarInsn(ASTORE, storage.local(value));
                return;
            case FIELD:
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(SWAP);
                mv.visitFieldInsn(PUTFIELD, internalClassName, storage.field(value), valueDesc(value));
                return;
            default:
                mv.visitInsn(POP);
        }
    }

    private void setCurrentPc(MethodVisitor mv, Bytecode bytecode) {
        if (bytecode == null) {
            return;
        }
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, bytecode.pc());
        mv.visitFieldInsn(PUTFIELD, internalClassName, "currentPc", "I");
    }

    private void emitSetPosFromCurrentPcOnStack(MethodVisitor mv) {
        mv.visitFieldInsn(GETSTATIC, internalClassName, "POS", LONG_ARRAY_DESC);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "currentPc", "I");
        mv.visitInsn(LALOAD);
        mv.visitMethodInsn(INVOKEVIRTUAL, SCRIPT_EXEC_EXCEPTION, "setPos", "(J)V", false);
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
        if (value.bytecode().isSpreadCall()) {
            emitValue(mv, graph, storage, value.operands().get(0));
            mv.visitTypeInsn(CHECKCAST, ARRAY_NODE);
            prepareSpreadArgs(mv);
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
            return;
        }
        int argc = value.operands().size();
        prepareArrayArgs(mv, graph, storage, value, argc);

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

    private void prepareArrayArgs(MethodVisitor mv, SsaGraph graph, StoragePlan storage, SsaValue value, int argc) {
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
        mv.visitFieldInsn(PUTFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
    }

    private void prepareSpreadArgs(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(SWAP);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "spreadArgs", ARRAY_NODE_DESC);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "args", JSON_NODE_ARRAY_DESC);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "argCnt", "I");
    }

    private void emitAsyncFunctionCall(MethodVisitor mv,
                                       SsaGraph graph,
                                       AsyncPlan asyncPlan,
                                       StoragePlan storage,
                                       SsaValue value,
                                       Map<SsaValue, Label> asyncLabels) {
        if (value.bytecode().isSpreadCall()) {
            emitValue(mv, graph, storage, value.operands().get(0));
            mv.visitTypeInsn(CHECKCAST, ARRAY_NODE);
            prepareSpreadArgs(mv);
        } else {
            prepareArrayArgs(mv, graph, storage, value, value.operands().size());
        }
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
        pushInt(mv, value.index());
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Library.AsyncFunction.class));
        mv.visitMethodInsn(INVOKEVIRTUAL, internalClassName, "callAsyncFunc",
                '(' + Type.getDescriptor(Library.AsyncFunction.class) + ")Z", false);
        emitAsyncReturnOrResume(mv, asyncPlan, value, asyncLabels);
    }

    private void emitAsyncConstCall(MethodVisitor mv,
                                    AsyncPlan asyncPlan,
                                    StoragePlan storage,
                                    SsaValue value,
                                    Map<SsaValue, Label> asyncLabels) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETSTATIC, internalClassName, "OPERANDS", OBJECT_ARRAY_DESC);
        pushInt(mv, value.index());
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Library.AsyncConstant.class));
        mv.visitMethodInsn(INVOKEVIRTUAL, internalClassName, "callAsyncConst",
                '(' + Type.getDescriptor(Library.AsyncConstant.class) + ")Z", false);
        emitAsyncReturnOrResume(mv, asyncPlan, value, asyncLabels);
    }

    private void emitAsyncReturnOrResume(MethodVisitor mv,
                                         AsyncPlan asyncPlan,
                                         SsaValue value,
                                         Map<SsaValue, Label> asyncLabels) {
        Label resume = asyncLabels.get(value);
        Label immediate = new Label();
        mv.visitJumpInsn(IFEQ, immediate);
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, asyncPlan.point(value).resumeId());
        mv.visitFieldInsn(PUTFIELD, internalClassName, "asyncState", "I");
        mv.visitInsn(RETURN);
        mv.visitLabel(immediate);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        pushInt(mv, AbstractVm.STAT_RUNNING);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "state", "I");
        mv.visitLabel(resume);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitFieldInsn(PUTFIELD, internalClassName, "asyncState", "I");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "rtError", SCRIPT_EXEC_EXCEPTION_DESC);
        Label success = new Label();
        mv.visitJumpInsn(IFNULL, success);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "rtError", SCRIPT_EXEC_EXCEPTION_DESC);
        emitSetPosFromCurrentPcOnStack(mv);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "rtError", SCRIPT_EXEC_EXCEPTION_DESC);
        mv.visitInsn(ATHROW);
        mv.visitLabel(success);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalClassName, "rtValue", JSON_NODE_DESC);
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

    private static String valueDesc(SsaValue value) {
        if (value != null && value.kind() == SsaKind.EXCEPTION) {
            return SCRIPT_EXEC_EXCEPTION_DESC;
        }
        if (value != null && value.kind() == SsaKind.PHI) {
            for (SsaValue operand : value.operands()) {
                if (operand.kind() == SsaKind.EXCEPTION) {
                    return SCRIPT_EXEC_EXCEPTION_DESC;
                }
            }
        }
        return JSON_NODE_DESC;
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
