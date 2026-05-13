package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.AbstractVm;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

public class CfgAotClassGenerator {

    public static final String INIT_OPERAND_METHOD = "__INIT_OPERAND";

    private static final AtomicLong ID = new AtomicLong();
    private static final String CLASS_PREFIX = "io/fiber/net/script/run/CfgGeneratedVm_";
    private static final String SUPER_NAME = Type.getInternalName(AbstractVm.class);
    private static final String JSON_NODE_NAME = Type.getInternalName(JsonNode.class);
    private static final String VALUE_NODE_NAME = Type.getInternalName(ValueNode.class);
    private static final String CONSTANT_NAME = Type.getInternalName(Library.Constant.class);
    private static final String ASYNC_CONSTANT_NAME = Type.getInternalName(Library.AsyncConstant.class);
    private static final String FUNCTION_NAME = Type.getInternalName(Library.Function.class);
    private static final String ASYNC_FUNCTION_NAME = Type.getInternalName(Library.AsyncFunction.class);
    private static final String SCRIPT_EXEC_NAME = Type.getInternalName(ScriptExecException.class);

    private static final String JSON_NODE_DESC = Type.getDescriptor(JsonNode.class);
    private static final String VALUE_NODE_DESC = Type.getDescriptor(ValueNode.class);
    private static final String CONSTANT_DESC = Type.getDescriptor(Library.Constant.class);
    private static final String ASYNC_CONSTANT_DESC = Type.getDescriptor(Library.AsyncConstant.class);
    private static final String FUNCTION_DESC = Type.getDescriptor(Library.Function.class);
    private static final String ASYNC_FUNCTION_DESC = Type.getDescriptor(Library.AsyncFunction.class);
    private static final String CONSTRUCTOR_DESC = "(" + JSON_NODE_DESC + "Ljava/lang/Object;" +
            Type.getDescriptor(Maybe.Emitter.class) + ")V";
    private static final String CONSTRUCTOR_SIGNATURE = "(" + JSON_NODE_DESC +
            "Ljava/lang/Object;Lio/fiber/net/common/async/Maybe$Emitter<Lio/fiber/net/common/json/JsonNode;>;)V";
    private static final String INIT_OPERAND_DESC = "([Ljava/lang/Object;)V";

    private final ValueAllocator.Result allocation;
    private final String internalClassName;
    private byte[] classData;

    public CfgAotClassGenerator(ValueAllocator.Result allocation) {
        this(allocation, CLASS_PREFIX + Long.toHexString(ID.getAndIncrement()));
    }

    CfgAotClassGenerator(ValueAllocator.Result allocation, String internalClassName) {
        this.allocation = allocation;
        this.internalClassName = internalClassName;
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
        emitFields(writer);
        emitInitOperand(writer);
        emitConstructor(writer);
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

    private void emitRun(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PROTECTED,
                "run",
                "()V",
                null,
                new String[]{SCRIPT_EXEC_NAME});
        visitor.visitCode();
        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
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
}
