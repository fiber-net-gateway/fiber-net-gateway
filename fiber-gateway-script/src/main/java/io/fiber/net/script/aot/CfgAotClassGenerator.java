package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.IteratorNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.AstUtils;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.Access;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Compares;
import io.fiber.net.script.run.Unaries;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CfgAotClassGenerator {

    public static final String INIT_OPERAND_METHOD = "__INIT_OPERAND";

    private static final AtomicLong ID = new AtomicLong();
    private static final String CLASS_PREFIX = "io/fiber/net/script/run/CfgGeneratedVm_";
    private static final String SUPER_NAME = Type.getInternalName(AbstractVm.class);
    private static final String JSON_NODE_NAME = Type.getInternalName(JsonNode.class);
    private static final String ARRAY_NODE_NAME = Type.getInternalName(ArrayNode.class);
    private static final String ITERATOR_NODE_NAME = Type.getInternalName(IteratorNode.class);
    private static final String BOOLEAN_NODE_NAME = Type.getInternalName(BooleanNode.class);
    private static final String VALUE_NODE_NAME = Type.getInternalName(ValueNode.class);
    private static final String CONSTANT_NAME = Type.getInternalName(Library.Constant.class);
    private static final String ASYNC_CONSTANT_NAME = Type.getInternalName(Library.AsyncConstant.class);
    private static final String FUNCTION_NAME = Type.getInternalName(Library.Function.class);
    private static final String ASYNC_FUNCTION_NAME = Type.getInternalName(Library.AsyncFunction.class);
    private static final String SCRIPT_EXEC_NAME = Type.getInternalName(ScriptExecException.class);
    private static final String JSON_UTIL_NAME = Type.getInternalName(JsonUtil.class);
    private static final String BINARIES_NAME = Type.getInternalName(Binaries.class);
    private static final String UNARIES_NAME = Type.getInternalName(Unaries.class);
    private static final String COMPARES_NAME = Type.getInternalName(Compares.class);
    private static final String ACCESS_NAME = Type.getInternalName(Access.class);

    private static final String JSON_NODE_DESC = Type.getDescriptor(JsonNode.class);
    private static final String ARRAY_NODE_DESC = Type.getDescriptor(ArrayNode.class);
    private static final String VALUE_NODE_DESC = Type.getDescriptor(ValueNode.class);
    private static final String CONSTANT_DESC = Type.getDescriptor(Library.Constant.class);
    private static final String ASYNC_CONSTANT_DESC = Type.getDescriptor(Library.AsyncConstant.class);
    private static final String FUNCTION_DESC = Type.getDescriptor(Library.Function.class);
    private static final String ASYNC_FUNCTION_DESC = Type.getDescriptor(Library.AsyncFunction.class);
    private static final String SCRIPT_EXEC_DESC = Type.getDescriptor(ScriptExecException.class);
    private static final String CONSTRUCTOR_DESC = "(" + JSON_NODE_DESC + "Ljava/lang/Object;" +
            Type.getDescriptor(Maybe.Emitter.class) + ")V";
    private static final String CONSTRUCTOR_SIGNATURE = "(" + JSON_NODE_DESC +
            "Ljava/lang/Object;Lio/fiber/net/common/async/Maybe$Emitter<Lio/fiber/net/common/json/JsonNode;>;)V";
    private static final String INIT_OPERAND_DESC = "([Ljava/lang/Object;)V";
    private static final String JSON_ARRAY_DESC = "[" + JSON_NODE_DESC;

    private static final String ASYNC_STATE_FIELD = "asyncState";
    private static final String FUNC_ARGS_FIELD = "funcArgs";
    private static final String SPREAD_ARGS_FIELD = "spreadArgs";

    private final Cfg cfg;
    private final Compiled compiled;
    private final AsyncSpillAnalysis.Result asyncSpills;
    private final SsaDestruction.Result ssaDestruction;
    private final ValueAllocator.Result allocation;
    private final String internalClassName;
    private final boolean hasCatchEdges;
    private final Map<SsaValue, Integer> runtimeLocalSlots = new IdentityHashMap<>();
    private final int firstTempLocal;
    private byte[] classData;

    public CfgAotClassGenerator(ValueAllocator.Result allocation) {
        this(null, null, null, null, allocation, CLASS_PREFIX + Long.toHexString(ID.getAndIncrement()));
    }

    CfgAotClassGenerator(ValueAllocator.Result allocation, String internalClassName) {
        this(null, null, null, null, allocation, internalClassName);
    }

    public CfgAotClassGenerator(Cfg cfg,
                                Compiled compiled,
                                AsyncSpillAnalysis.Result asyncSpills,
                                SsaDestruction.Result ssaDestruction,
                                ValueAllocator.Result allocation) {
        this(cfg, compiled, asyncSpills, ssaDestruction, allocation,
                CLASS_PREFIX + Long.toHexString(ID.getAndIncrement()));
    }

    CfgAotClassGenerator(Cfg cfg,
                         Compiled compiled,
                         AsyncSpillAnalysis.Result asyncSpills,
                         SsaDestruction.Result ssaDestruction,
                         ValueAllocator.Result allocation,
                         String internalClassName) {
        this.cfg = cfg;
        this.compiled = compiled;
        this.asyncSpills = asyncSpills;
        this.ssaDestruction = ssaDestruction;
        this.allocation = allocation;
        this.internalClassName = internalClassName;
        this.hasCatchEdges = hasThrowEdges(cfg);
        int slot = 1;
        for (SsaValue value : allocation.orderedValues()) {
            if (allocation.locationOf(value).getKind() == ValueAllocator.Location.Kind.LOCAL) {
                runtimeLocalSlots.put(value, slot++);
            }
        }
        this.firstTempLocal = slot;
    }

    private static boolean hasThrowEdges(Cfg cfg) {
        if (cfg == null) {
            return false;
        }
        for (Block block : cfg.getBlocks()) {
            for (Edge edge : block.getSuccessors()) {
                if (edge.getType() == Edge.Type.THROW) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getInternalClassName() {
        return internalClassName;
    }

    public String getClassName() {
        return internalClassName.replace('/', '.');
    }

    public byte[] generateClassData() {
        if (classData != null) {
            return classData;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL,
                internalClassName,
                null,
                SUPER_NAME,
                null);
        if (compiled != null && compiled.getFileName() != null) {
            writer.visitSource(compiled.getFileName(), null);
        }
        emitFields(writer);
        emitInitOperand(writer);
        emitConstructor(writer);
        emitGetArgVal(writer);
        emitGetArgCnt(writer);
        emitRun(writer);
        writer.visitEnd();
        return classData = writer.toByteArray();
    }

    public Class<?> loadAsClass() throws Exception {
        Class<?> clz = CfgAotGeneratedClassLoader.loadClz(getClassName(), generateClassData());
        Method method = clz.getDeclaredMethod(INIT_OPERAND_METHOD, Object[].class);
        method.invoke(null, new Object[]{allocation.staticOperands().initOperands()});
        return clz;
    }

    private void emitFields(ClassWriter writer) {
        ValueAllocator.StaticOperands operands = allocation.staticOperands();
        for (ValueAllocator.LiteralField field : operands.getLiterals()) {
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    field.getFieldName(),
                    field.getFieldDesc(),
                    null,
                    null).visitEnd();
        }
        for (ValueAllocator.OperandField<Library.Constant> field : operands.getConstants()) {
            emitStaticField(writer, field.getFieldName(), CONSTANT_DESC);
        }
        for (ValueAllocator.OperandField<Library.AsyncConstant> field : operands.getAsyncConstants()) {
            emitStaticField(writer, field.getFieldName(), ASYNC_CONSTANT_DESC);
        }
        for (ValueAllocator.OperandField<Library.Function> field : operands.getFunctions()) {
            emitStaticField(writer, field.getFieldName(), FUNCTION_DESC);
        }
        for (ValueAllocator.OperandField<Library.AsyncFunction> field : operands.getAsyncFunctions()) {
            emitStaticField(writer, field.getFieldName(), ASYNC_FUNCTION_DESC);
        }
        for (SsaValue value : allocation.asyncFieldValues()) {
            ValueAllocator.AsyncFieldLocation location =
                    (ValueAllocator.AsyncFieldLocation) allocation.locationOf(value);
            writer.visitField(Opcodes.ACC_PRIVATE,
                    location.getFieldName(),
                    JSON_NODE_DESC,
                    null,
                    null).visitEnd();
        }
        writer.visitField(Opcodes.ACC_PRIVATE, ASYNC_STATE_FIELD, "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, FUNC_ARGS_FIELD, JSON_ARRAY_DESC, null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, SPREAD_ARGS_FIELD, ARRAY_NODE_DESC, null, null).visitEnd();
    }

    private void emitStaticField(ClassWriter writer, String name, String desc) {
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, desc, null, null).visitEnd();
    }

    private void emitInitOperand(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                INIT_OPERAND_METHOD,
                INIT_OPERAND_DESC,
                null,
                null);
        visitor.visitCode();
        int index = 0;
        for (ValueAllocator.LiteralField field : allocation.staticOperands().getLiterals()) {
            emitStaticOperandLoad(visitor, index++, field.getFieldDesc().equals(VALUE_NODE_DESC) ? VALUE_NODE_NAME : JSON_NODE_NAME);
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, internalClassName, field.getFieldName(), field.getFieldDesc());
        }
        for (ValueAllocator.OperandField<Library.Constant> field : allocation.staticOperands().getConstants()) {
            emitInitField(visitor, index++, field.getFieldName(), CONSTANT_NAME, CONSTANT_DESC);
        }
        for (ValueAllocator.OperandField<Library.AsyncConstant> field : allocation.staticOperands().getAsyncConstants()) {
            emitInitField(visitor, index++, field.getFieldName(), ASYNC_CONSTANT_NAME, ASYNC_CONSTANT_DESC);
        }
        for (ValueAllocator.OperandField<Library.Function> field : allocation.staticOperands().getFunctions()) {
            emitInitField(visitor, index++, field.getFieldName(), FUNCTION_NAME, FUNCTION_DESC);
        }
        for (ValueAllocator.OperandField<Library.AsyncFunction> field : allocation.staticOperands().getAsyncFunctions()) {
            emitInitField(visitor, index++, field.getFieldName(), ASYNC_FUNCTION_NAME, ASYNC_FUNCTION_DESC);
        }
        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void emitInitField(MethodVisitor visitor, int index, String fieldName, String typeName, String desc) {
        emitStaticOperandLoad(visitor, index, typeName);
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, internalClassName, fieldName, desc);
    }

    private void emitStaticOperandLoad(MethodVisitor visitor, int index, String typeName) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(visitor, index);
        visitor.visitInsn(Opcodes.AALOAD);
        visitor.visitTypeInsn(Opcodes.CHECKCAST, typeName);
    }

    private void emitConstructor(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>",
                CONSTRUCTOR_DESC,
                CONSTRUCTOR_SIGNATURE,
                null);
        visitor.visitCode();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitVarInsn(Opcodes.ALOAD, 1);
        visitor.visitVarInsn(Opcodes.ALOAD, 2);
        visitor.visitVarInsn(Opcodes.ALOAD, 3);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, SUPER_NAME, "<init>", CONSTRUCTOR_DESC, false);
        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void emitGetArgVal(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC,
                "getArgVal",
                "(I)" + JSON_NODE_DESC,
                null,
                null);
        visitor.visitCode();
        Label normal = new Label();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName, SPREAD_ARGS_FIELD, ARRAY_NODE_DESC);
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitJumpInsn(Opcodes.IFNULL, normal);
        visitor.visitVarInsn(Opcodes.ILOAD, 1);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ARRAY_NODE_NAME, "path", "(I)" + JSON_NODE_DESC, false);
        visitor.visitInsn(Opcodes.ARETURN);

        visitor.visitLabel(normal);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName, FUNC_ARGS_FIELD, JSON_ARRAY_DESC);
        visitor.visitVarInsn(Opcodes.ILOAD, 1);
        visitor.visitInsn(Opcodes.AALOAD);
        visitor.visitInsn(Opcodes.ARETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void emitGetArgCnt(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC,
                "getArgCnt",
                "()I",
                null,
                null);
        visitor.visitCode();
        Label normal = new Label();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName, SPREAD_ARGS_FIELD, ARRAY_NODE_DESC);
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitJumpInsn(Opcodes.IFNULL, normal);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ARRAY_NODE_NAME, "size", "()I", false);
        visitor.visitInsn(Opcodes.IRETURN);

        visitor.visitLabel(normal);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName, FUNC_ARGS_FIELD, JSON_ARRAY_DESC);
        visitor.visitInsn(Opcodes.ARRAYLENGTH);
        visitor.visitInsn(Opcodes.IRETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void emitRun(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PROTECTED,
                "run",
                "()V",
                null,
                new String[]{SCRIPT_EXEC_NAME});
        visitor.visitCode();
        if (cfg == null) {
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
            return;
        }

        CodegenContext context = new CodegenContext(visitor);
        emitAsyncDispatch(context);
        for (Block block : cfg.getBlocks()) {
            visitor.visitLabel(context.label(block));
            emitBlock(context, block);
            emitImplicitTransfer(context, block);
        }
        emitCatchHandlers(context);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void emitAsyncDispatch(CodegenContext context) {
        List<Instruction> asyncInstructions = asyncSpills == null
                ? java.util.Collections.<Instruction>emptyList()
                : asyncSpills.getAsyncInstructions();
        if (asyncInstructions.isEmpty()) {
            return;
        }
        MethodVisitor visitor = context.visitor;
        Label start = new Label();
        Label[] labels = new Label[asyncInstructions.size() + 1];
        labels[0] = start;
        for (int i = 0; i < asyncInstructions.size(); i++) {
            Label label = new Label();
            labels[i + 1] = label;
            context.resumeLabels.put(asyncInstructions.get(i), label);
            context.asyncIds.put(asyncInstructions.get(i), i + 1);
        }
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName, ASYNC_STATE_FIELD, "I");
        visitor.visitTableSwitchInsn(0, asyncInstructions.size(), start, labels);
        visitor.visitLabel(start);
    }

    private void emitBlock(CodegenContext context, Block block) {
        ActiveTry activeTry = null;
        for (Instruction instruction : block.getInstructions()) {
            CatchHandler handler = catchHandlerFor(context, instruction);
            if (activeTry != null && shouldCloseTry(activeTry.handler, handler, instruction)) {
                context.visitor.visitLabel(activeTry.end);
                activeTry = null;
            }
            if (activeTry == null && handler != null) {
                activeTry = startTry(context, handler);
            }
            emitLineNumber(context, instruction.getPc());
            emitInstruction(context, instruction);
        }
        if (activeTry != null) {
            context.visitor.visitLabel(activeTry.end);
        }
    }

    private CatchHandler catchHandlerFor(CodegenContext context, Instruction instruction) {
        if (!hasCatchEdges || instruction.canThrow() == Instruction.Throw.NOT
                || instruction instanceof io.fiber.net.script.aot.Throw) {
            return null;
        }
        if (ConstantThrow.of(instruction) != null) {
            return null;
        }
        Edge edge = throwEdge(instruction.getBelongTo());
        if (edge == null || !isHandledByEdge(instruction, edge)) {
            return null;
        }
        return context.catchHandler(edge);
    }

    private boolean isHandledByEdge(Instruction instruction, Edge edge) {
        if (compiled == null || compiled.getExpIns() == null) {
            return true;
        }
        int handlerPc = Compiled.searchExpHandle(instruction.getPc(), compiled.getExpIns());
        return handlerPc >= 0 && edge.getSuccessor().startPc == handlerPc;
    }

    private static boolean shouldCloseTry(CatchHandler active, CatchHandler next, Instruction instruction) {
        if (next != null) {
            return active != next;
        }
        return instruction.canThrow() != Instruction.Throw.NOT;
    }

    private ActiveTry startTry(CodegenContext context, CatchHandler handler) {
        Label begin = new Label();
        Label end = new Label();
        context.visitor.visitTryCatchBlock(begin, end, handler.label, SCRIPT_EXEC_NAME);
        context.visitor.visitLabel(begin);
        return new ActiveTry(end, handler);
    }

    private void emitCatchHandlers(CodegenContext context) {
        for (CatchHandler handler : context.catchHandlers) {
            context.visitor.visitLabel(handler.label);
            context.visitor.visitVarInsn(Opcodes.ASTORE, context.exceptionLocal());
            storeRtError(context.visitor, context.exceptionLocal());
            emitEdgeTransfer(context, handler.edge);
        }
    }

    private void emitInstruction(CodegenContext context, Instruction instruction) {
        if (instruction instanceof LoadRoot || instruction instanceof LoadConst || instruction instanceof Phi) {
            return;
        }
        if (instruction instanceof Expr && isStackValue(((Expr) instruction).getResult())) {
            return;
        }
        ConstantThrow.Template constantThrow = ConstantThrow.of(instruction);
        if (constantThrow != null) {
            emitConstantThrow(context, instruction, constantThrow);
            return;
        }
        if (instruction instanceof NewObj) {
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, JSON_UTIL_NAME, "createObjectNode",
                    "()Lio/fiber/net/common/json/ObjectNode;", false);
            storeExprResult(context, (Expr) instruction);
            return;
        }
        if (instruction instanceof NewArr) {
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, JSON_UTIL_NAME, "createArrayNode",
                    "()Lio/fiber/net/common/json/ArrayNode;", false);
            storeExprResult(context, (Expr) instruction);
            return;
        }
        if (instruction instanceof Binary) {
            emitBinary(context, (Binary) instruction);
            return;
        }
        if (instruction instanceof Unary) {
            emitUnary(context, (Unary) instruction);
            return;
        }
        if (instruction instanceof IndexGet) {
            IndexGet indexGet = (IndexGet) instruction;
            loadValue(context, indexGet.getOwner());
            loadValue(context, indexGet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "indexGet",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, indexGet);
            return;
        }
        if (instruction instanceof IndexSet) {
            IndexSet indexSet = (IndexSet) instruction;
            loadValue(context, indexSet.getOwner());
            loadValue(context, indexSet.getKey());
            loadValue(context, indexSet.getAlien());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "indexSet",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, indexSet);
            return;
        }
        if (instruction instanceof IndexSet1) {
            IndexSet1 indexSet = (IndexSet1) instruction;
            loadValue(context, indexSet.getOwner());
            loadValue(context, indexSet.getKey());
            loadValue(context, indexSet.getAlien());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "indexSet1",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, indexSet);
            return;
        }
        if (instruction instanceof PropGet) {
            PropGet propGet = (PropGet) instruction;
            loadValue(context, propGet.getOwner());
            context.visitor.visitLdcInsn(propGet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "propGet",
                    "(" + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
            storeExprResult(context, propGet);
            return;
        }
        if (instruction instanceof PropSet) {
            PropSet propSet = (PropSet) instruction;
            loadValue(context, propSet.getOwner());
            loadValue(context, propSet.getAlien());
            context.visitor.visitLdcInsn(propSet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "propSet",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
            storeExprResult(context, propSet);
            return;
        }
        if (instruction instanceof PropSet1) {
            PropSet1 propSet = (PropSet1) instruction;
            loadValue(context, propSet.getOwner());
            loadValue(context, propSet.getAlien());
            context.visitor.visitLdcInsn(propSet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "propSet1",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
            storeExprResult(context, propSet);
            return;
        }
        if (instruction instanceof ExpandObj) {
            ExpandObj expandObj = (ExpandObj) instruction;
            loadValue(context, expandObj.getTarget());
            loadValue(context, expandObj.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "expandObject",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, expandObj);
            return;
        }
        if (instruction instanceof ExpandArr) {
            ExpandArr expandArr = (ExpandArr) instruction;
            loadValue(context, expandArr.getTarget());
            loadValue(context, expandArr.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "expandArray",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, expandArr);
            return;
        }
        if (instruction instanceof PushArr) {
            PushArr pushArr = (PushArr) instruction;
            loadValue(context, pushArr.getTarget());
            loadValue(context, pushArr.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "pushArray",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            storeExprResult(context, pushArr);
            return;
        }
        if (instruction instanceof CallConst) {
            emitCallConst(context, (CallConst) instruction);
            return;
        }
        if (instruction instanceof CallFunc) {
            emitCallFunc(context, (CallFunc) instruction);
            return;
        }
        if (instruction instanceof CallAsyncConst) {
            emitCallAsyncConst(context, (CallAsyncConst) instruction);
            return;
        }
        if (instruction instanceof CallAsyncFunc) {
            emitCallAsyncFunc(context, (CallAsyncFunc) instruction);
            return;
        }
        if (instruction instanceof CatchError) {
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SUPER_NAME, "errorToObj",
                    "()" + JSON_NODE_DESC, false);
            storeExprResult(context, (CatchError) instruction);
            return;
        }
        if (instruction instanceof Jump) {
            emitEdgeTransfer(context, normalEdgeTo(instruction.getBelongTo(), ((Jump) instruction).getTarget()));
            return;
        }
        if (instruction instanceof JumpIfFalse) {
            JumpIfFalse jump = (JumpIfFalse) instruction;
            emitConditional(context, jump.getBelongTo(), jump.getTarget(), jump.getCond(), false);
            return;
        }
        if (instruction instanceof JumpIfTrue) {
            JumpIfTrue jump = (JumpIfTrue) instruction;
            emitConditional(context, jump.getBelongTo(), jump.getTarget(), jump.getCond(), true);
            return;
        }
        if (instruction instanceof Ret) {
            loadValue(context, ((Ret) instruction).getValue());
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitInsn(Opcodes.SWAP);
            context.visitor.visitFieldInsn(Opcodes.PUTFIELD, SUPER_NAME, "rtValue", JSON_NODE_DESC);
            endSuccess(context.visitor);
            return;
        }
        if (instruction instanceof RetV) {
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitInsn(Opcodes.ACONST_NULL);
            context.visitor.visitFieldInsn(Opcodes.PUTFIELD, SUPER_NAME, "rtValue", JSON_NODE_DESC);
            endSuccess(context.visitor);
            return;
        }
        if (instruction instanceof io.fiber.net.script.aot.Throw) {
            if (throwEdge(instruction.getBelongTo()) == null) {
                context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
                loadValue(context, ((io.fiber.net.script.aot.Throw) instruction).value);
                context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SUPER_NAME, "objToError",
                        "(" + JSON_NODE_DESC + ")" + SCRIPT_EXEC_DESC, false);
                context.visitor.visitInsn(Opcodes.ATHROW);
                return;
            }
            loadValue(context, ((io.fiber.net.script.aot.Throw) instruction).value);
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitInsn(Opcodes.SWAP);
            context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SUPER_NAME, "objToError",
                    "(" + JSON_NODE_DESC + ")" + SCRIPT_EXEC_DESC, false);
            context.visitor.visitVarInsn(Opcodes.ASTORE, context.exceptionLocal());
            storeRtError(context.visitor, context.exceptionLocal());
            emitThrowTransfer(context, instruction.getBelongTo());
            return;
        }
        throw new IllegalStateException("unsupported instruction " + instruction.getClass().getName());
    }

    private void emitConstantThrow(CodegenContext context, Instruction instruction, ConstantThrow.Template template) {
        newScriptExecException(context.visitor, template);
        if (throwEdge(instruction.getBelongTo()) == null) {
            context.visitor.visitInsn(Opcodes.ATHROW);
            return;
        }
        context.visitor.visitVarInsn(Opcodes.ASTORE, context.exceptionLocal());
        storeRtError(context.visitor, context.exceptionLocal());
        emitThrowTransfer(context, instruction.getBelongTo());
    }

    private void emitBinary(CodegenContext context, Binary binary) {
        emitBinaryValue(context, binary);
        storeExprResult(context, binary);
    }

    private void emitBinaryValue(CodegenContext context, Binary binary) {
        loadValue(context, binary.getLeft());
        loadValue(context, binary.getRight());
        context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, BINARIES_NAME, binaryMethod(binary.getOp()),
                binaryDesc(binary.getOp()), false);
    }

    private void emitUnary(CodegenContext context, Unary unary) {
        emitUnaryValue(context, unary);
        storeExprResult(context, unary);
    }

    private void emitUnaryValue(CodegenContext context, Unary unary) {
        loadValue(context, unary.getMaterial());
        switch (unary.getOp()) {
            case PLUS:
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, UNARIES_NAME, "plus",
                        "(" + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
                break;
            case MINUS:
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, UNARIES_NAME, "minus",
                        "(" + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
                break;
            case NEG:
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, UNARIES_NAME, "neg",
                        "(" + JSON_NODE_DESC + ")Lio/fiber/net/common/json/BooleanNode;", false);
                break;
            case TYPEOF:
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, UNARIES_NAME, "typeof",
                        "(" + JSON_NODE_DESC + ")Lio/fiber/net/common/json/TextNode;", false);
                break;
            case ITERATE_INTO:
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, UNARIES_NAME, "iterate",
                        "(" + JSON_NODE_DESC + ")Lio/fiber/net/common/json/IteratorNode;", false);
                break;
            case ITERATE_NEXT:
                context.visitor.visitTypeInsn(Opcodes.CHECKCAST, ITERATOR_NODE_NAME);
                context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITERATOR_NODE_NAME, "next", "()Z", false);
                context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, BOOLEAN_NODE_NAME, "valueOf",
                        "(Z)Lio/fiber/net/common/json/BooleanNode;", false);
                break;
            case ITERATE_KEY:
                context.visitor.visitTypeInsn(Opcodes.CHECKCAST, ITERATOR_NODE_NAME);
                context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITERATOR_NODE_NAME, "currentKey",
                        "()" + JSON_NODE_DESC, false);
                break;
            case ITERATE_VALUE:
                context.visitor.visitTypeInsn(Opcodes.CHECKCAST, ITERATOR_NODE_NAME);
                context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITERATOR_NODE_NAME, "currentValue",
                        "()" + JSON_NODE_DESC, false);
                break;
            default:
                throw new IllegalStateException("[bug] unknown unary op");
        }
    }

    private void emitCallConst(CodegenContext context, CallConst call) {
        ValueAllocator.OperandField<Library.Constant> field = findOperandField(allocation.staticOperands().getConstants(), call.getConstant());
        context.visitor.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, field.getFieldName(), CONSTANT_DESC);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONSTANT_NAME, "get",
                "(" + Type.getDescriptor(ExecutionContext.class) + ")" + JSON_NODE_DESC, true);
        emitNullNode(context.visitor);
        storeExprResult(context, call);
    }

    private void emitCallFunc(CodegenContext context, CallFunc call) {
        prepareArgs(context, call.isSpread(), call.getArgs());
        ValueAllocator.OperandField<Library.Function> field = findOperandField(allocation.staticOperands().getFunctions(), call.getFunction());
        context.visitor.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, field.getFieldName(), FUNCTION_DESC);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, FUNCTION_NAME, "call",
                "(" + Type.getDescriptor(ExecutionContext.class) + Type.getDescriptor(Library.Arguments.class) + ")" + JSON_NODE_DESC,
                true);
        emitNullNode(context.visitor);
        storeExprResult(context, call);
    }

    private void emitCallAsyncConst(CodegenContext context, CallAsyncConst call) {
        ValueAllocator.OperandField<Library.AsyncConstant> field =
                findOperandField(allocation.staticOperands().getAsyncConstants(), call.getConstant());
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, field.getFieldName(), ASYNC_CONSTANT_DESC);
        context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SUPER_NAME, "callAsyncConst",
                "(" + ASYNC_CONSTANT_DESC + ")Z", false);
        emitAsyncReturnOrResume(context, call);
    }

    private void emitCallAsyncFunc(CodegenContext context, CallAsyncFunc call) {
        prepareArgs(context, call.isSpread(), call.getArgs());
        ValueAllocator.OperandField<Library.AsyncFunction> field =
                findOperandField(allocation.staticOperands().getAsyncFunctions(), call.getFunction());
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, field.getFieldName(), ASYNC_FUNCTION_DESC);
        context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SUPER_NAME, "callAsyncFunc",
                "(" + ASYNC_FUNCTION_DESC + ")Z", false);
        emitAsyncReturnOrResume(context, call);
    }

    private void emitAsyncReturnOrResume(CodegenContext context, Expr asyncExpr) {
        Label resume = context.resumeLabels.get(asyncExpr);
        Integer asyncId = context.asyncIds.get(asyncExpr);
        if (resume == null || asyncId == null) {
            throw new IllegalStateException("[bug] missing async resume label");
        }
        Label immediate = new Label();
        context.visitor.visitJumpInsn(Opcodes.IFEQ, immediate);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(context.visitor, asyncId);
        context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, ASYNC_STATE_FIELD, "I");
        context.visitor.visitInsn(Opcodes.RETURN);

        context.visitor.visitLabel(immediate);
        emitSetRunning(context.visitor);
        context.visitor.visitLabel(resume);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitInsn(Opcodes.ICONST_0);
        context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, ASYNC_STATE_FIELD, "I");
        emitSetRunning(context.visitor);

        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitFieldInsn(Opcodes.GETFIELD, SUPER_NAME, "rtError", SCRIPT_EXEC_DESC);
        Label success = new Label();
        context.visitor.visitJumpInsn(Opcodes.IFNULL, success);
        emitThrowTransfer(context, asyncExpr.getBelongTo());
        context.visitor.visitLabel(success);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitFieldInsn(Opcodes.GETFIELD, SUPER_NAME, "rtValue", JSON_NODE_DESC);
        storeExprResult(context, asyncExpr);
    }

    private void emitConditional(CodegenContext context, Block block, Block target, SsaValue cond, boolean trueJump) {
        if (isStackValue(cond)) {
            emitStackCondition(context, cond);
        } else {
            loadValue(context, cond);
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, COMPARES_NAME, "logic",
                    "(" + JSON_NODE_DESC + ")Z", false);
        }
        Label taken = new Label();
        context.visitor.visitJumpInsn(trueJump ? Opcodes.IFNE : Opcodes.IFEQ, taken);
        emitEdgeTransfer(context, otherNormalEdgeOrOnly(block, target));
        context.visitor.visitLabel(taken);
        emitEdgeTransfer(context, normalEdgeTo(block, target));
    }

    private void emitStackCondition(CodegenContext context, SsaValue cond) {
        Expr assign = cond.getAssign();
        if (assign instanceof Binary) {
            Binary binary = (Binary) assign;
            loadValue(context, binary.getLeft());
            loadValue(context, binary.getRight());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, COMPARES_NAME, binaryMethod(binary.getOp()),
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")Z", false);
            return;
        }
        if (assign instanceof Unary) {
            Unary unary = (Unary) assign;
            loadValue(context, unary.getMaterial());
            switch (unary.getOp()) {
                case NEG:
                    context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, COMPARES_NAME, "neg",
                            "(" + JSON_NODE_DESC + ")Z", false);
                    return;
                case ITERATE_NEXT:
                    context.visitor.visitTypeInsn(Opcodes.CHECKCAST, ITERATOR_NODE_NAME);
                    context.visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITERATOR_NODE_NAME, "next", "()Z", false);
                    return;
                default:
                    break;
            }
        }
        loadValue(context, cond);
        context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, COMPARES_NAME, "logic",
                "(" + JSON_NODE_DESC + ")Z", false);
    }

    private void emitStackExpr(CodegenContext context, Expr expr) {
        if (expr instanceof NewObj) {
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, JSON_UTIL_NAME, "createObjectNode",
                    "()Lio/fiber/net/common/json/ObjectNode;", false);
            return;
        }
        if (expr instanceof NewArr) {
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, JSON_UTIL_NAME, "createArrayNode",
                    "()Lio/fiber/net/common/json/ArrayNode;", false);
            return;
        }
        if (expr instanceof Binary) {
            emitBinaryValue(context, (Binary) expr);
            return;
        }
        if (expr instanceof Unary) {
            emitUnaryValue(context, (Unary) expr);
            return;
        }
        if (expr instanceof IndexGet) {
            IndexGet indexGet = (IndexGet) expr;
            loadValue(context, indexGet.getOwner());
            loadValue(context, indexGet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "indexGet",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof PropGet) {
            PropGet propGet = (PropGet) expr;
            loadValue(context, propGet.getOwner());
            context.visitor.visitLdcInsn(propGet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "propGet",
                    "(" + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof IndexSet1) {
            IndexSet1 indexSet = (IndexSet1) expr;
            loadValue(context, indexSet.getOwner());
            loadValue(context, indexSet.getKey());
            loadValue(context, indexSet.getAlien());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "indexSet1",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof PropSet1) {
            PropSet1 propSet = (PropSet1) expr;
            loadValue(context, propSet.getOwner());
            loadValue(context, propSet.getAlien());
            context.visitor.visitLdcInsn(propSet.getKey());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "propSet1",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + "Ljava/lang/String;)" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof ExpandObj) {
            ExpandObj expandObj = (ExpandObj) expr;
            loadValue(context, expandObj.getTarget());
            loadValue(context, expandObj.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "expandObject",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof ExpandArr) {
            ExpandArr expandArr = (ExpandArr) expr;
            loadValue(context, expandArr.getTarget());
            loadValue(context, expandArr.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "expandArray",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            return;
        }
        if (expr instanceof PushArr) {
            PushArr pushArr = (PushArr) expr;
            loadValue(context, pushArr.getTarget());
            loadValue(context, pushArr.getAddition());
            context.visitor.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS_NAME, "pushArray",
                    "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
            return;
        }
        throw new IllegalStateException("[bug] unsupported stack expr " + expr.getClass().getName());
    }

    private void emitImplicitTransfer(CodegenContext context, Block block) {
        List<Instruction> instructions = block.getInstructions();
        if (!instructions.isEmpty()) {
            Instruction last = instructions.get(instructions.size() - 1);
            if (last instanceof Jump || last instanceof JumpIfFalse || last instanceof JumpIfTrue
                    || last instanceof Ret || last instanceof RetV || last instanceof io.fiber.net.script.aot.Throw) {
                return;
            }
        }
        Edge edge = singleNormalEdge(block);
        if (edge != null) {
            emitEdgeTransfer(context, edge);
        }
    }

    private void emitEdgeTransfer(CodegenContext context, Edge edge) {
        if (edge == null) {
            context.visitor.visitInsn(Opcodes.RETURN);
            return;
        }
        emitEdgeCopies(context, edge);
        context.visitor.visitJumpInsn(Opcodes.GOTO, context.label(edge.getSuccessor()));
    }

    private void emitThrowTransfer(CodegenContext context, Block block) {
        Edge edge = throwEdge(block);
        if (edge == null) {
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitFieldInsn(Opcodes.GETFIELD, SUPER_NAME, "rtError", SCRIPT_EXEC_DESC);
            context.visitor.visitInsn(Opcodes.ATHROW);
            return;
        }
        emitEdgeTransfer(context, edge);
    }

    private void emitEdgeCopies(CodegenContext context, Edge edge) {
        if (ssaDestruction == null) {
            return;
        }
        SsaDestruction.EdgeCopy edgeCopy = ssaDestruction.getEdgeCopy(edge);
        if (edgeCopy == null || edgeCopy.getMoves().isEmpty()) {
            return;
        }
        int idx = 0;
        for (SsaDestruction.Move move : edgeCopy.getMoves()) {
            loadValue(context, move.getSrc());
            context.visitor.visitVarInsn(Opcodes.ASTORE, context.copyTempLocal(idx++));
        }
        idx = 0;
        for (SsaDestruction.Move move : edgeCopy.getMoves()) {
            context.visitor.visitVarInsn(Opcodes.ALOAD, context.copyTempLocal(idx++));
            storeValue(context.visitor, move.getDst());
        }
    }

    private void prepareArgs(CodegenContext context, boolean spread, SsaValue[] args) {
        if (spread) {
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            loadValue(context, args[0]);
            context.visitor.visitTypeInsn(Opcodes.CHECKCAST, ARRAY_NODE_NAME);
            context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, SPREAD_ARGS_FIELD, ARRAY_NODE_DESC);
            context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitor.visitInsn(Opcodes.ACONST_NULL);
            context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, FUNC_ARGS_FIELD, JSON_ARRAY_DESC);
            return;
        }
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        context.visitor.visitInsn(Opcodes.ACONST_NULL);
        context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, SPREAD_ARGS_FIELD, ARRAY_NODE_DESC);
        context.visitor.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(context.visitor, args.length);
        context.visitor.visitTypeInsn(Opcodes.ANEWARRAY, JSON_NODE_NAME);
        for (int i = 0; i < args.length; i++) {
            context.visitor.visitInsn(Opcodes.DUP);
            pushInt(context.visitor, i);
            loadValue(context, args[i]);
            context.visitor.visitInsn(Opcodes.AASTORE);
        }
        context.visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, FUNC_ARGS_FIELD, JSON_ARRAY_DESC);
    }

    private void loadValue(CodegenContext context, SsaValue value) {
        if (isStackValue(value)) {
            emitStackExpr(context, value.getAssign());
            return;
        }
        loadValue(context.visitor, value);
    }

    private void loadValue(MethodVisitor visitor, SsaValue value) {
        ValueAllocator.Location location = allocation.locationOf(value);
        switch (location.getKind()) {
            case LOCAL:
                visitor.visitVarInsn(Opcodes.ALOAD, runtimeLocalSlot(value));
                return;
            case STACK:
                throw new IllegalStateException("[bug] stack value cannot be loaded as local value");
            case ASYNC_FIELD:
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitFieldInsn(Opcodes.GETFIELD, internalClassName,
                        ((ValueAllocator.AsyncFieldLocation) location).getFieldName(), JSON_NODE_DESC);
                return;
            case ROOT_FIELD:
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitFieldInsn(Opcodes.GETFIELD, SUPER_NAME, "root", JSON_NODE_DESC);
                return;
            case STATIC_LITERAL: {
                ValueAllocator.StaticLiteralLocation literal = (ValueAllocator.StaticLiteralLocation) location;
                visitor.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, literal.getFieldName(), literal.getFieldDesc());
                if (literal.isCopyOnLoad()) {
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_NODE_NAME, "deepCopy", "()" + JSON_NODE_DESC, false);
                }
                return;
            }
            default:
                throw new IllegalStateException("[bug] unknown location");
        }
    }

    private void storeExprResult(CodegenContext context, Expr expr) {
        storeValue(context.visitor, expr.getResult());
    }

    private void storeValue(MethodVisitor visitor, SsaValue value) {
        ValueAllocator.Location location = allocation.locationOf(value);
        switch (location.getKind()) {
            case LOCAL:
                visitor.visitVarInsn(Opcodes.ASTORE, runtimeLocalSlot(value));
                return;
            case STACK:
                throw new IllegalStateException("[bug] stack value cannot be stored as local value");
            case ASYNC_FIELD:
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitInsn(Opcodes.SWAP);
                visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClassName,
                        ((ValueAllocator.AsyncFieldLocation) location).getFieldName(), JSON_NODE_DESC);
                return;
            case ROOT_FIELD:
            case STATIC_LITERAL:
                visitor.visitInsn(Opcodes.POP);
                return;
            default:
                throw new IllegalStateException("[bug] unknown location");
        }
    }

    private void emitLineNumber(CodegenContext context, int pc) {
        if (compiled == null || compiled.getPos() == null || pc < 0 || pc >= compiled.getPos().length) {
            return;
        }
        int line = compiled.getLineByOffset(AstUtils.startPos(compiled.getPos()[pc]));
        if (line <= 0 || line == context.lastLineNumber) {
            return;
        }
        Label label = new Label();
        context.visitor.visitLabel(label);
        context.visitor.visitLineNumber(line, label);
        context.lastLineNumber = line;
    }

    private int runtimeLocalSlot(SsaValue value) {
        Integer slot = runtimeLocalSlots.get(value);
        if (slot == null) {
            throw new IllegalStateException("[bug] missing runtime local slot");
        }
        return slot;
    }

    private boolean isStackValue(SsaValue value) {
        return allocation.locationOf(value).getKind() == ValueAllocator.Location.Kind.STACK;
    }

    private static void emitNullNode(MethodVisitor visitor) {
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, SUPER_NAME, "nullNode",
                "(" + JSON_NODE_DESC + ")" + JSON_NODE_DESC, false);
    }

    private static void emitSetRunning(MethodVisitor visitor) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(visitor, AbstractVm.STAT_RUNNING);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, SUPER_NAME, "state", "I");
    }

    private static void storeRtError(MethodVisitor visitor, int exceptionLocal) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitVarInsn(Opcodes.ALOAD, exceptionLocal);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, SUPER_NAME, "rtError", SCRIPT_EXEC_DESC);
    }

    private static void newScriptExecException(MethodVisitor visitor, ConstantThrow.Template template) {
        visitor.visitTypeInsn(Opcodes.NEW, SCRIPT_EXEC_NAME);
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitLdcInsn(template.message);
        pushInt(visitor, template.code);
        visitor.visitLdcInsn(template.errorName);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, SCRIPT_EXEC_NAME, "<init>",
                "(Ljava/lang/String;ILjava/lang/String;)V", false);
    }

    private static void endSuccess(MethodVisitor visitor) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(visitor, AbstractVm.STAT_END_SEC);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, SUPER_NAME, "state", "I");
        visitor.visitInsn(Opcodes.RETURN);
    }

    private Edge normalEdgeTo(Block block, Block target) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.getType() != Edge.Type.THROW && edge.getSuccessor() == target) {
                return edge;
            }
        }
        throw new IllegalStateException("[bug] missing edge to " + target.startPc);
    }

    private Edge otherNormalEdge(Block block, Block target) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.getType() != Edge.Type.THROW && edge.getSuccessor() != target) {
                return edge;
            }
        }
        throw new IllegalStateException("[bug] missing fallthrough edge");
    }

    private Edge otherNormalEdgeOrOnly(Block block, Block target) {
        Edge single = null;
        for (Edge edge : block.getSuccessors()) {
            if (edge.getType() == Edge.Type.THROW) {
                continue;
            }
            if (edge.getSuccessor() != target) {
                return edge;
            }
            single = edge;
        }
        if (single != null) {
            return single;
        }
        throw new IllegalStateException("[bug] missing normal edge");
    }

    private Edge singleNormalEdge(Block block) {
        Edge result = null;
        for (Edge edge : block.getSuccessors()) {
            if (edge.getType() == Edge.Type.THROW) {
                continue;
            }
            if (result != null) {
                throw new IllegalStateException("[bug] multiple implicit normal edges");
            }
            result = edge;
        }
        return result;
    }

    private Edge throwEdge(Block block) {
        for (Edge edge : block.getSuccessors()) {
            if (edge.getType() == Edge.Type.THROW) {
                return edge;
            }
        }
        return null;
    }

    private static String binaryMethod(Binary.Op op) {
        switch (op) {
            case PLUS:
                return "plus";
            case MINUS:
                return "minus";
            case MULTIPLY:
                return "multiply";
            case DIVIDE:
                return "divide";
            case MOD:
                return "modulo";
            case MATCH:
                return "matches";
            case LT:
                return "lt";
            case LTE:
                return "lte";
            case GT:
                return "gt";
            case GTE:
                return "gte";
            case EQ:
                return "eq";
            case SEQ:
                return "seq";
            case NE:
                return "ne";
            case SNE:
                return "sne";
            case IN:
                return "in";
            default:
                throw new IllegalStateException("[bug] unknown binary op");
        }
    }

    private static String binaryDesc(Binary.Op op) {
        String ret = op.ordinal() < Binary.Op.MATCH.ordinal()
                ? JSON_NODE_DESC
                : "Lio/fiber/net/common/json/BooleanNode;";
        return "(" + JSON_NODE_DESC + JSON_NODE_DESC + ")" + ret;
    }

    private static <T> ValueAllocator.OperandField<T> findOperandField(List<ValueAllocator.OperandField<T>> fields, T value) {
        for (ValueAllocator.OperandField<T> field : fields) {
            if (field.getValue() == value) {
                return field;
            }
        }
        throw new IllegalStateException("[bug] missing static operand");
    }

    private static void pushInt(MethodVisitor visitor, int value) {
        if (value >= -1 && value <= 5) {
            visitor.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            visitor.visitLdcInsn(value);
        }
    }

    private class CodegenContext {
        final MethodVisitor visitor;
        final Map<Block, Label> labels = new IdentityHashMap<>();
        final Map<Instruction, Label> resumeLabels = new IdentityHashMap<>();
        final Map<Instruction, Integer> asyncIds = new IdentityHashMap<>();
        final Map<Object, CatchHandler> catchHandlersByKey = new IdentityHashMap<>();
        final List<CatchHandler> catchHandlers = new ArrayList<>();
        final int tempBase;
        final int exceptionLocal;

        CodegenContext(MethodVisitor visitor) {
            this.visitor = visitor;
            int maxCopies = 0;
            if (ssaDestruction != null) {
                for (SsaDestruction.EdgeCopy edgeCopy : ssaDestruction.getEdgeCopies()) {
                    maxCopies = Math.max(maxCopies, edgeCopy.getMoves().size());
                }
            }
            this.tempBase = firstTempLocal;
            this.exceptionLocal = tempBase + maxCopies + 1;
        }

        Label label(Block block) {
            return labels.computeIfAbsent(block, k -> new Label());
        }

        int copyTempLocal(int idx) {
            return tempBase + idx;
        }

        int exceptionLocal() {
            return exceptionLocal;
        }

        CatchHandler catchHandler(Edge edge) {
            Object key = catchHandlerKey(edge);
            CatchHandler handler = catchHandlersByKey.get(key);
            if (handler == null) {
                handler = new CatchHandler(edge);
                catchHandlersByKey.put(key, handler);
                catchHandlers.add(handler);
            }
            return handler;
        }

        private Object catchHandlerKey(Edge edge) {
            SsaDestruction.EdgeCopy edgeCopy = ssaDestruction == null ? null : ssaDestruction.getEdgeCopy(edge);
            if (edgeCopy == null || edgeCopy.getMoves().isEmpty()) {
                return edge.getSuccessor();
            }
            return edge;
        }

        int lastLineNumber = -1;
    }

    private static class ActiveTry {
        final Label end;
        final CatchHandler handler;

        ActiveTry(Label end, CatchHandler handler) {
            this.end = end;
            this.handler = handler;
        }
    }

    private static class CatchHandler {
        final Edge edge;
        final Label label = new Label();

        CatchHandler(Edge edge) {
            this.edge = edge;
        }
    }
}
