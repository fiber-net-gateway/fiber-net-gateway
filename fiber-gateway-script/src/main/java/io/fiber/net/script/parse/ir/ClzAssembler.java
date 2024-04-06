package io.fiber.net.script.parse.ir;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.Access;
import io.fiber.net.script.run.Binaries;
import io.fiber.net.script.run.Unaries;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

public class ClzAssembler {

    private static final String JSON_FIELD_TYPE_NAME = Type.getInternalName(JsonNode.class);
    private static final String JSON_FIELD_TYPE_DESC = Type.getDescriptor(JsonNode.class);
    private static final String JSON_VALUE_TYPE_NAME = Type.getInternalName(ValueNode.class);
    private static final String JSON_VALUE_TYPE_DESC = Type.getDescriptor(ValueNode.class);
    //    private static final String JSON_CONTAINER_TYPE_NAME = Type.getInternalName(ContainerNode.class);
    private static final String CONSTANT_FIELD_TYPE_NAME = Type.getInternalName(Library.Constant.class);
    private static final String CONSTANT_FIELD_TYPE_DESC = Type.getDescriptor(Library.Constant.class);
    private static final String ASYNC_CONST_FIELD_TYPE_NAME = Type.getInternalName(Library.AsyncConstant.class);
    private static final String ASYNC_CONST_FIELD_TYPE_DESC = Type.getDescriptor(Library.AsyncConstant.class);
    private static final String FUNC_TYPE_NAME = Type.getInternalName(Library.Function.class);
    private static final String FUNC_TYPE_DESC = Type.getDescriptor(Library.Function.class);
    private static final String ASYNC_FUNC_TYPE_NAME = Type.getInternalName(Library.AsyncFunction.class);
    private static final String BINARIES_TYPE_NAME = Type.getInternalName(Binaries.class);
    private static final String UNARIES_TYPE_NAME = Type.getInternalName(Unaries.class);
    private static final String JSON_UTIL_NAME = Type.getInternalName(JsonUtil.class);
    private static final String ACCESS_NAME = Type.getInternalName(Access.class);
    private static final String INIT_CLZ_MTD_DESC = "([Ljava/lang/Object;)V";
    private static final String[] STACK_FIELD_NAMES = new String[16];
    private static final String[] BINARY_MTD_NAMES = initBinaryName();
    private static final String[] UNARY_MTD_NAMES = initUnaryName();

    private static final AtomicLong ID = new AtomicLong();
    private static final String[] INTERFACES = new String[0];
    private static final String SUPER_NAME = Type.getInternalName(AbstractVm.class);
    private static final String CLZ_PREFIX = "io/fiber/net/script/run/GeneratedVm_";
    private static final String CLZ_CONSTRUCTOR_DESC = "(Lio/fiber/net/common/json/JsonNode;Ljava/lang/Object;)V";
    private static final String CLZ_GET_ARG_CNT_DESC = "()I";
    private static final String CLZ_GET_ARG_VAL_DESC = "(I)Lio/fiber/net/common/json/JsonNode;";
    private static final String CLZ_EXEC_DESC = "()Lio/fiber/net/common/async/Maybe;";
    private static final String CLZ_EXEC_SIGNATURE = "()Lio/fiber/net/common/async/Maybe<Lio/fiber/net/common/json/JsonNode;>;";
    private static final String CLZ_RESUME_DESC = "()V";
    private static final String DEEP_COPY_DESC = "()Lio/fiber/net/common/json/JsonNode;";
    private static final String CREATE_OBJ_DESC = "()Lio/fiber/net/common/json/ObjectNode;";
    private static final String CREATE_ARRAY_DESC = "()Lio/fiber/net/common/json/ArrayNode;";
    private static final String BINARY_JSON_DESC = "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/JsonNode;";
    private static final String BINARY_BOOL_DESC = "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/BooleanNode;";
    private static final String[] CLZ_SCRIPT_EXEC_EXP = new String[]{Type.getInternalName(ScriptExecException.class)};
    static final String INIT_CLZ_NAME = "__INIT_OPERAND__";

    private final ClassWriter writer = new ClassWriter(0);
    private final Compiled compiled;

    private String internalClzName;
    private ExtOperand<String> propOperand;
    private ExtOperand<JsonNode> literalExtOperand;
    private ExtOperand<Library.Constant> constantExtOperand;
    private ExtOperand<Library.AsyncConstant> asyncConstantExtOperand;
    private ExtOperand<Library.Function> functionExtOperand;
    private ExtOperand<Library.AsyncFunction> asyncFunctionExtOperand;
    private int staticOperandFieldLength;

    private MethodVisitor visitor;

    public int addStringProp(int idx) {
        if (propOperand == null) {
            propOperand = new ExtOperand<>();
        }
        return propOperand.addIdx(idx, (String) compiled.getOperands()[idx]);
    }

    public int addConstant(int idx) {
        if (constantExtOperand == null) {
            constantExtOperand = new ExtOperand<>();
        }
        return constantExtOperand.addIdx(idx, (Library.Constant) compiled.getOperands()[idx]);
    }

    public int addAsyncConstant(int idx) {
        if (asyncConstantExtOperand == null) {
            asyncConstantExtOperand = new ExtOperand<>();
        }
        return asyncConstantExtOperand.addIdx(idx, (Library.AsyncConstant) compiled.getOperands()[idx]);
    }

    public int addFunction(int idx) {
        if (functionExtOperand == null) {
            functionExtOperand = new ExtOperand<>();
        }
        return functionExtOperand.addIdx(idx, (Library.Function) compiled.getOperands()[idx]);
    }

    public int addAsyncFunction(int idx) {
        if (asyncFunctionExtOperand == null) {
            asyncFunctionExtOperand = new ExtOperand<>();
        }
        return asyncFunctionExtOperand.addIdx(idx, (Library.AsyncFunction) compiled.getOperands()[idx]);
    }

    public int addLiteralExtOperand(int idx) {
        if (literalExtOperand == null) {
            literalExtOperand = new ExtOperand<>();
        }
        return literalExtOperand.addIdx(idx, (JsonNode) compiled.getOperands()[idx]);
    }

    public String getInternalClzName() {
        return internalClzName;
    }

    public Class<?> asmClz() throws Throwable {
        byte[] bytes = asmClzData();
        String clzName = internalClzName.replace('/', '.');
        Class<?> clz = AotGeneratedClassLoader.loadClz(clzName, bytes);
        Method method = clz.getDeclaredMethod(INIT_CLZ_NAME, Object[].class);
        method.invoke(null, new Object[]{initClzParam()});
        return clz;
    }

    public byte[] asmClzData() throws Throwable {
        asmRunSuffix();
        writer.visitEnd();
        return writer.toByteArray();
    }

    Object[] initClzParam() {
        Object[] objects = new Object[staticOperandFieldLength];
        int idx = 0;
        if (literalExtOperand != null) {
            for (ExtOperand.OperandKey<JsonNode> key : literalExtOperand.getKeyList()) {
                objects[idx++] = key.getKey();
            }
        }
        if (constantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Constant> key : constantExtOperand.getKeyList()) {
                objects[idx++] = key.getKey();
            }
        }

        if (asyncConstantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncConstant> key : asyncConstantExtOperand.getKeyList()) {
                objects[idx++] = key.getKey();

            }
        }
        if (functionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Function> key : functionExtOperand.getKeyList()) {
                objects[idx++] = key.getKey();

            }
        }
        if (asyncFunctionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncFunction> key : asyncFunctionExtOperand.getKeyList()) {
                objects[idx++] = key.getKey();
            }
        }
        return objects;
    }

    public void preAms() {
        internalClzName = CLZ_PREFIX + Long.toHexString(ID.getAndIncrement());
        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL,
                internalClzName,
                null,
                SUPER_NAME,
                INTERFACES
        );
        asmFields();
        asmInitClz();
        asmConstructor();

        asmGetArgVal();
        asmGetArgCnt();
        asmExec();
        asmResume();
        asmRunPrefix();
    }


    private Label startLabel = new Label();
    private Label endLabel = new Label();

    private void asmRunPrefix() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PRIVATE, "run",
                "()V", null, CLZ_SCRIPT_EXEC_EXP
        );
        methodVisitor.visitCode();
        visitor = methodVisitor;
        visitor.visitLabel(startLabel);
        // FIXME ....
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
//        visitor.visitInsn(Opcodes.SWAP);
    }

    private void asmRunSuffix() {
        // FIXME ....
        visitor.visitLabel(endLabel);
        visitor.visitMaxs(5, 1);
        visitor.visitLocalVariable("this", "L" + internalClzName + ";", null, startLabel, endLabel, 0);
//        visitor.visitFrame(Opcodes.F_FULL, 1, new Object[]{
//                Opcodes.UNINITIALIZED_THIS
//        }, 0, null);
        visitor.visitEnd();
        visitor = null;
    }

    private void asmFields() {
        if (literalExtOperand != null) {
            for (ExtOperand.OperandKey<JsonNode> key : literalExtOperand.getKeyList()) {
                if (key.getKey().isContainerNode()) {
                    writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                            getLiteralVarName(key),
                            JSON_FIELD_TYPE_DESC,
                            null,
                            null
                    );
                } else {
                    writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                            getLiteralVarName(key),
                            JSON_VALUE_TYPE_DESC,
                            null,
                            null
                    );
                }
            }
        }
        if (constantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Constant> key : constantExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "_CONSTANT_" + key.getId(),
                        CONSTANT_FIELD_TYPE_DESC,
                        null,
                        null
                );
            }
        }

        if (asyncConstantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncConstant> key : asyncConstantExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "_ASYNC_CONSTANT_" + key.getId(),
                        ASYNC_CONST_FIELD_TYPE_NAME,
                        ASYNC_CONST_FIELD_TYPE_DESC,
                        null
                );
            }
        }
        if (functionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Function> key : functionExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "_FUNC_" + key.getId(),
                        FUNC_TYPE_NAME,
                        null,
                        null
                );
            }
        }
        if (asyncFunctionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncFunction> key : asyncFunctionExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "_ASYNC_FUNC_" + key.getId(),
                        ASYNC_FUNC_TYPE_NAME,
                        null,
                        null
                );
            }
        }

        writer.visitField(Opcodes.ACC_PRIVATE,
                "asyncState", "I", null, null).visitEnd();

        int stackSize = compiled.getStackSize();
        for (int i = 0; i < stackSize; i++) {
            writer.visitField(Opcodes.ACC_PRIVATE,
                    getStackFieldName(i), JSON_FIELD_TYPE_DESC, null, null).visitEnd();
        }
    }

    private static void constBiPush(MethodVisitor methodVisitor, int c) {
        switch (c) {
            case 0:
                methodVisitor.visitInsn(Opcodes.ICONST_0);
                break;
            case 1:
                methodVisitor.visitInsn(Opcodes.ICONST_1);
                break;
            case 2:
                methodVisitor.visitInsn(Opcodes.ICONST_2);
                break;
            case 3:
                methodVisitor.visitInsn(Opcodes.ICONST_3);
                break;
            case 4:
                methodVisitor.visitInsn(Opcodes.ICONST_4);
                break;
            case 5:
                methodVisitor.visitInsn(Opcodes.ICONST_5);
                break;
            default:
                methodVisitor.visitIntInsn(Opcodes.BIPUSH, c);
                break;
        }
    }

    private void asmInitClz() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                INIT_CLZ_NAME,
                INIT_CLZ_MTD_DESC,
                null,
                null
        );
        methodVisitor.visitCode();
        int initClzLocals = 0;
        if (literalExtOperand != null) {
            for (ExtOperand.OperandKey<JsonNode> key : literalExtOperand.getKeyList()) {
                if (key.getKey().isContainerNode()) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    constBiPush(methodVisitor, initClzLocals++);
                    methodVisitor.visitInsn(Opcodes.AALOAD);
                    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, JSON_FIELD_TYPE_NAME);
                    methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                            internalClzName,
                            getLiteralVarName(key),
                            JSON_FIELD_TYPE_DESC
                    );
                } else {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    constBiPush(methodVisitor, initClzLocals++);
                    methodVisitor.visitInsn(Opcodes.AALOAD);
                    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, JSON_VALUE_TYPE_NAME);
                    methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                            internalClzName,
                            getLiteralVarName(key),
                            JSON_VALUE_TYPE_DESC
                    );
                }
            }
        }
        if (constantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Constant> key : constantExtOperand.getKeyList()) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                constBiPush(methodVisitor, initClzLocals++);
                methodVisitor.visitInsn(Opcodes.AALOAD);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, CONSTANT_FIELD_TYPE_NAME);
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                        internalClzName,
                        "_CONSTANT_" + key.getId(),
                        CONSTANT_FIELD_TYPE_DESC);
            }
        }

        if (asyncConstantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncConstant> key : asyncConstantExtOperand.getKeyList()) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                constBiPush(methodVisitor, initClzLocals++);
                methodVisitor.visitInsn(Opcodes.AALOAD);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, ASYNC_CONST_FIELD_TYPE_NAME);
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                        internalClzName,
                        "_ASYNC_CONSTANT_" + key.getId(),
                        ASYNC_CONST_FIELD_TYPE_DESC
                );
            }
        }
        if (functionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Function> key : functionExtOperand.getKeyList()) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                constBiPush(methodVisitor, initClzLocals++);
                methodVisitor.visitInsn(Opcodes.AALOAD);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, FUNC_TYPE_NAME);
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                        internalClzName,
                        "_FUNC_" + key.getId(),
                        FUNC_TYPE_NAME
                );
            }
        }
        if (asyncFunctionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncFunction> key : asyncFunctionExtOperand.getKeyList()) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                constBiPush(methodVisitor, initClzLocals++);
                methodVisitor.visitInsn(Opcodes.AALOAD);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, ASYNC_FUNC_TYPE_NAME);
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC,
                        internalClzName,
                        "_ASYNC_FUNC_" + key.getId(),
                        ASYNC_FUNC_TYPE_NAME
                );
            }
        }
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
        staticOperandFieldLength = initClzLocals;
    }

    private static String getLiteralVarName(ExtOperand.OperandKey<JsonNode> key) {
        return "_LITERAL_" + key.getId();
    }

    private void asmConstructor() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                CLZ_CONSTRUCTOR_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitMaxs(3, 3);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                SUPER_NAME,
                "<init>",
                CLZ_CONSTRUCTOR_DESC,
                false
        );
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();
    }

    private void asmGetArgVal() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "getArgVal",
                CLZ_GET_ARG_VAL_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(1, 2);
        methodVisitor.visitEnd();
    }

    private void asmGetArgCnt() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "getArgCnt",
                CLZ_GET_ARG_CNT_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitInsn(Opcodes.ICONST_0);
        methodVisitor.visitInsn(Opcodes.IRETURN);
        methodVisitor.visitMaxs(1, 2);
        methodVisitor.visitEnd();
    }

    private void asmExec() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "exec",
                CLZ_EXEC_DESC, CLZ_EXEC_SIGNATURE, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitEnd();
    }

    private void asmResume() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "resume",
                CLZ_RESUME_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();
    }


    private static String[] initUnaryName() {
        String[] nameArr = new String[Unary.TYPES.length];
        for (Unary.Type type : Unary.TYPES) {
            String mtdName = type.name().toLowerCase();
            try {
                Unaries.class.getDeclaredMethod(mtdName, JsonNode.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace(System.err);
                throw new IllegalStateException("change Binaries method??? ->" + mtdName, e);
            }
            nameArr[type.ordinal()] = mtdName;
        }
        return nameArr;
    }

    static {
        for (int i = 0; i < STACK_FIELD_NAMES.length; i++) {
            STACK_FIELD_NAMES[i] = ("_stack_" + i).intern();
        }
    }


    private static String[] initBinaryName() {
        String[] nameArr = new String[Binary.TYPES.length];
        for (Binary.Type type : Binary.TYPES) {
            initBinaryName(nameArr, type);
        }
        return nameArr;
    }

    static String getBinariesTypeName() {
        return BINARIES_TYPE_NAME;
    }

    private static void initBinaryName(String[] nameArr, Binary.Type type) {
        String mtdName = type.name().toLowerCase();
        try {
            Binaries.class.getDeclaredMethod(mtdName, JsonNode.class, JsonNode.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(System.err);
            throw new IllegalStateException("change Binaries method??? ->" + mtdName, e);
        }
        nameArr[type.ordinal()] = mtdName;
    }


    private static String getStackFieldName(int sp) {
        return sp < STACK_FIELD_NAMES.length ? STACK_FIELD_NAMES[sp] : "_stack_" + sp;
    }


    public ClzAssembler(Compiled compiled) {
        this.compiled = compiled;
    }

    void stashStack(int sp) {
        Assert.isTrue(sp <= compiled.getStackSize());
        visitor.visitFieldInsn(Opcodes.PUTFIELD,
                internalClzName,
                getStackFieldName(sp),
                JSON_FIELD_TYPE_NAME
        );
    }

    void restoreStack(int sp) {
        Assert.isTrue(sp <= compiled.getStackSize());
        visitor.visitFieldInsn(Opcodes.GETFIELD,
                internalClzName,
                getStackFieldName(sp),
                JSON_FIELD_TYPE_NAME
        );
    }

    void binary(Binary.Type type) {
        String desc = type.ordinal() >= Binary.Type.MATCHES.ordinal() ? BINARY_BOOL_DESC : BINARY_JSON_DESC;
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                BINARIES_TYPE_NAME,
                BINARY_MTD_NAMES[type.ordinal()],
                desc,
                false
        );
    }

    void unary(Unary.Type type) {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                UNARIES_TYPE_NAME,
                UNARY_MTD_NAMES[type.ordinal()],
                null,
                false
        );
    }

    void indexGet() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "indexGet",
                BINARY_JSON_DESC,
                false
        );
    }

    void indexSet() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "indexSet",
                "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/JsonNode;",
                false
        );
    }

    void propGet(int keyId) {
        visitor.visitLdcInsn(propOperand.getKeyList().get(keyId).getKey());
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "propGet",
                "(Lio/fiber/net/common/json/JsonNode;Ljava/lang/String;)Lio/fiber/net/common/json/JsonNode;",
                false
        );
    }

    void propSet(int keyId) {
        visitor.visitLdcInsn(propOperand.getKeyList().get(keyId).getKey());
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "propSet",
                "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;Ljava/lang/String;)Lio/fiber/net/common/json/JsonNode;",
                false
        );
    }

    void propSet1(int keyId) {
        visitor.visitLdcInsn(propOperand.getKeyList().get(keyId).getKey());
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "propSet1",
                "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;Ljava/lang/String;)Lio/fiber/net/common/json/JsonNode;",
                false
        );
    }

    void pushArray() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "pushArray",
                BINARY_JSON_DESC,
                false
        );
    }

    void expArray() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "expandArray",
                BINARY_JSON_DESC,
                false
        );
    }

    void expObject() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                ACCESS_NAME,
                "expandObject",
                BINARY_JSON_DESC,
                false
        );
    }

    void returnResult() {
//        visitor.visitVarInsn(Opcodes.ALOAD, 0);
//        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, "rtValue", JSON_FIELD_TYPE_DESC);
        returnV();
    }

    void returnV() {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        constBiPush(visitor, AbstractVm.STAT_END_SEC);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, "state", "I");
        visitor.visitInsn(Opcodes.RETURN);
    }

    void loadConst(int constId) {
        ExtOperand.OperandKey<JsonNode> key = literalExtOperand.getKeyList().get(constId);
        if (key.getKey().isContainerNode()) {
            visitor.visitFieldInsn(Opcodes.GETSTATIC,
                    internalClzName,
                    getLiteralVarName(key),
                    JSON_FIELD_TYPE_DESC
            );
            visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    JSON_FIELD_TYPE_NAME,
                    "deepCopy",
                    DEEP_COPY_DESC,
                    false
            );
        } else {
            visitor.visitFieldInsn(Opcodes.GETSTATIC,
                    internalClzName,
                    getLiteralVarName(key),
                    JSON_VALUE_TYPE_DESC
            );
        }
    }

    void loadRoot() {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD,
                internalClzName,
                "root",
                JSON_FIELD_TYPE_DESC
        );
    }

    void dump() {
        visitor.visitInsn(Opcodes.DUP);
    }

    void pop() {
        visitor.visitInsn(Opcodes.POP);
    }

    void newObj() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                JSON_UTIL_NAME,
                "createObjectNode",
                CREATE_OBJ_DESC,
                false
        );
    }

    void newArray() {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                JSON_UTIL_NAME,
                "createArrayNode",
                CREATE_ARRAY_DESC,
                false
        );
    }


}
