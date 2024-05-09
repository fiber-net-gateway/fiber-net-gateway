package io.fiber.net.script.parse.ir;

import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.run.*;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public class ClzAssembler {

    private static final String ARRAY_NODE_TYPE_NAME = Type.getInternalName(ArrayNode.class);
    private static final String JSON_FIELD_TYPE_NAME = Type.getInternalName(JsonNode.class);
    private static final String JSON_ITERATOR_TYPE_NAME = Type.getInternalName(IteratorNode.class);
    private static final String JSON_BOOLEAN_TYPE_NAME = Type.getInternalName(BooleanNode.class);
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
    private static final String ASYNC_FUNC_TYPE_DESC = Type.getDescriptor(Library.AsyncFunction.class);
    private static final String SCRIPT_EXEC_EXCEPTION_NAME = Type.getInternalName(ScriptExecException.class);
    private static final String SCRIPT_EXEC_DESC = Type.getDescriptor(ScriptExecException.class);
    private static final String BINARIES_TYPE_NAME = Type.getInternalName(Binaries.class);
    private static final String UNARIES_TYPE_NAME = Type.getInternalName(Unaries.class);
    private static final String JSON_UTIL_NAME = Type.getInternalName(JsonUtil.class);
    private static final String COMPARES_NAME = Type.getInternalName(Compares.class);
    private static final String ACCESS_NAME = Type.getInternalName(Access.class);
    private static final String INIT_CLZ_MTD_DESC = "([Ljava/lang/Object;)V";
    private static final String[] BINARY_MTD_NAMES = initBinaryName();
    private static final String[] UNARY_MTD_NAMES = initUnaryName();
    private static final String[] UNARY_MTD_DESC = initUnaryDESC();
    private static final CacheVarName LITERAL_NAME_CACHE = new CacheVarName("_LITERAL_", 16);
    private static final CacheVarName STACK_NAME_CACHE = new CacheVarName("_stack_", 16);
    private static final CacheVarName ASYNC_VAR_NAME_CACHE = new CacheVarName("_async_var_", 16);
    private static final CacheVarName FUNC_NAME_CACHE = new CacheVarName("_FUNC_", 16);
    private static final CacheVarName ASYNC_FUNC_NAME_CACHE = new CacheVarName("_ASYNC_FUNC_", 16);
    private static final CacheVarName CONST_NAME_CACHE = new CacheVarName("_CONST_", 16);
    private static final CacheVarName ASYNC_CONST_NAME_CACHE = new CacheVarName("_ASYNC_CONST_", 16);
    private static final CacheVarName LOCAL_VAR_NAME_CACHE = new CacheVarName("_local_", 16);

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
    private static final String UNSAFE_UNTIL_NAME = Type.getInternalName(UnsafeUtil.class);
    private static final String STACK_OFFSET_NAME = "STACK_OFFSET";
    private static final String JSON_NODE_OCCUPY_NAME = "JSON_NODE_OCCUPY";
    private static final String ASYNC_STATE_NAME = "asyncState";
    private static final String FUNC_ARGC_NAME = "funcArgc";
    private static final String FUNC_PARAM_SP_NAME = "funcParamSP";
    private static final String SPREAD_NAME = "spread";
    private static final String[] CLZ_SCRIPT_EXEC_EXP = new String[]{Type.getInternalName(ScriptExecException.class)};
    private static final Object[] STACK_LOCAL_TYPE = initStackLocalTypes();
    private static final Object[] STACK_EXCEPTION_TYPE = new Object[]{SCRIPT_EXEC_EXCEPTION_NAME};

    private static Object[] initStackLocalTypes() {
        Object[] strings = new Object[16];
        for (int i = 0; i < 16; i++) {
            strings[i] = JSON_FIELD_TYPE_NAME;
        }
        return strings;
    }

    static final String INIT_CLZ_NAME = "__INIT_OPERAND__";

    // FIXME use COMPUTE_MAXS for performance
    private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    private final Compiled compiled;

    private String internalClzName;
    private ExtOperand<String> propOperand;
    private ExtOperand<JsonNode> literalExtOperand;
    private ExtOperand<Library.Constant> constantExtOperand;
    private ExtOperand<Library.AsyncConstant> asyncConstantExtOperand;
    private ExtOperand<Library.Function> functionExtOperand;
    private ExtOperand<Library.AsyncFunction> asyncFunctionExtOperand;
    private ExtOperand<Label> asyncPoint;
    private Label asyncDefaultLabel;
    private int staticOperandFieldLength;
    private int maxStashStack;
    private int maxAsyncVarTableSize;
    private int maxLocalVarTableSize;
    private TreeMap<Integer, ExpTableItem> expTableMap;

    private MethodVisitor visitor;

    void setExceptionTable(TreeMap<Integer, ExpTableItem> expTableMap) {
        this.expTableMap = expTableMap;
    }

    public void setMaxStashStack(int maxStashStack) {
        this.maxStashStack = maxStashStack;
    }

    public void setMaxAsyncVarTableSize(int maxAsyncVarTableSize) {
        this.maxAsyncVarTableSize = maxAsyncVarTableSize;
    }

    public void setMaxLocalVarTableSize(int maxLocalVarTableSize) {
        this.maxLocalVarTableSize = maxLocalVarTableSize;
    }

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

    public int addAsyncPoint() {
        if (asyncPoint == null) {
            asyncPoint = new ExtOperand<>();
        }
        int id = asyncPoint.getKeyList().size();
        return asyncPoint.addIdx(id, new Label());
    }

    public String getInternalClzName() {
        return internalClzName;
    }

    Class<?> loadAsClz(byte[] bytes) throws Throwable {
        String clzName = internalClzName.replace('/', '.');
        Class<?> clz = AotGeneratedClassLoader.loadClz(clzName, bytes);
        Method method = clz.getDeclaredMethod(INIT_CLZ_NAME, Object[].class);
        method.invoke(null, new Object[]{initClzParam()});
        return clz;
    }

    public byte[] asmClzData() {
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
        asmRunPrefix();
    }


    private final Label startLabel = new Label();
    private final Label endLabel = new Label();

    private void asmRunPrefix() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PROTECTED, "run",
                "()V", null, CLZ_SCRIPT_EXEC_EXP
        );
        methodVisitor.visitCode();
        visitor = methodVisitor;
        visitor.visitLabel(startLabel);
        if (asyncPoint != null) {
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, ASYNC_STATE_NAME, "I");
            int size = asyncPoint.getKeyList().size();
            Label[] labels = new Label[size + 1];
            Label asyncStartLabel = new Label();
            labels[0] = asyncStartLabel;
            int i = 1;
            for (ExtOperand.OperandKey<Label> key : asyncPoint.getKeyList()) {
                labels[i++] = key.getKey();
            }
            visitor.visitTableSwitchInsn(0, size, asyncDefaultLabel = new Label(), labels);
            visitor.visitLabel(asyncStartLabel);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
    }

    private void asmRunSuffix() {
        if (asyncDefaultLabel != null) {
            visitor.visitLabel(asyncDefaultLabel);
            visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            visitor.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
            visitor.visitInsn(Opcodes.DUP);
            visitor.visitLdcInsn("[BUG] not hit asyncState");
            visitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/IllegalStateException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
            );
            visitor.visitInsn(Opcodes.ATHROW);
        }

        if (CollectionUtils.isNotEmpty(expTableMap)) {
            for (Map.Entry<Integer, ExpTableItem> entry : expTableMap.entrySet()) {
                ExpTableItem value = entry.getValue();
                visitor.visitTryCatchBlock(value.getTryStartLabel(), value.getTryEndLabel(), value.getCatchLabel(), SCRIPT_EXEC_EXCEPTION_NAME);
            }
        }
        visitor.visitLocalVariable("this", "L" + internalClzName + ";", null, startLabel, endLabel, 0);
        for (int i = 1; i <= maxLocalVarTableSize; i++) {
            visitor.visitLocalVariable(LOCAL_VAR_NAME_CACHE.getNameById(i), JSON_FIELD_TYPE_DESC, null, startLabel, endLabel, i);
        }
        visitor.visitLabel(endLabel);
        visitor.visitMaxs(0, 0);
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
                        CONST_NAME_CACHE.getNameById(key.getId()),
                        CONSTANT_FIELD_TYPE_DESC,
                        null,
                        null
                );
            }
        }

        if (asyncConstantExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncConstant> key : asyncConstantExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        ASYNC_CONST_NAME_CACHE.getNameById(key.getId()),
                        ASYNC_CONST_FIELD_TYPE_NAME,
                        ASYNC_CONST_FIELD_TYPE_DESC,
                        null
                );
            }
        }
        if (functionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.Function> key : functionExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        getSyncFuncName(key),
                        FUNC_TYPE_DESC,
                        null,
                        null
                );
            }
        }
        if (asyncFunctionExtOperand != null) {
            for (ExtOperand.OperandKey<Library.AsyncFunction> key : asyncFunctionExtOperand.getKeyList()) {
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        getAsyncFuncName(key),
                        ASYNC_FUNC_TYPE_DESC,
                        null,
                        null
                );
            }
        }

        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                STACK_OFFSET_NAME, "J", null, null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                JSON_NODE_OCCUPY_NAME, "J", null, null);

        writer.visitField(Opcodes.ACC_PRIVATE,
                ASYNC_STATE_NAME, "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE,
                FUNC_PARAM_SP_NAME, "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE,
                FUNC_ARGC_NAME, "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE,
                SPREAD_NAME, "Z", null, null).visitEnd();

        for (int i = 0; i < maxStashStack; i++) {
            writer.visitField(Opcodes.ACC_PRIVATE,
                    getStackFieldName(i), JSON_FIELD_TYPE_DESC, null, null).visitEnd();
        }
        for (int i = 0; i < maxAsyncVarTableSize; i++) {
            writer.visitField(Opcodes.ACC_PRIVATE,
                    ASYNC_VAR_NAME_CACHE.getNameById(i),
                    JSON_FIELD_TYPE_DESC, null, null).visitEnd();
        }

        MethodVisitor cInit = writer.visitMethod(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null, null
        );
        Label tryBeginLabel = new Label();
        cInit.visitLabel(tryBeginLabel);
        if (maxStashStack > 0) {
            cInit.visitLdcInsn(Type.getType("L" + internalClzName + ";"));
            cInit.visitLdcInsn(getStackFieldName(0));
            cInit.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getDeclaredField",
                    "(Ljava/lang/String;)Ljava/lang/reflect/Field;",
                    false
            );
            cInit.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "io/fiber/net/script/run/UnsafeUtil",
                    "getObjectOffset",
                    "(Ljava/lang/reflect/Field;)J",
                    false
            );
        } else {
            cInit.visitLdcInsn(0L);
        }
        cInit.visitFieldInsn(Opcodes.PUTSTATIC,
                internalClzName,
                STACK_OFFSET_NAME,
                "J");
        cInit.visitMethodInsn(Opcodes.INVOKESTATIC,
                "io/fiber/net/script/run/UnsafeUtil",
                "getJsonNodeOccupy",
                "()J",
                false
        );
        cInit.visitFieldInsn(Opcodes.PUTSTATIC,
                internalClzName,
                JSON_NODE_OCCUPY_NAME,
                "J");
        Label tryEndLabel = new Label();
        cInit.visitLabel(tryEndLabel);
        Label catchEndLabel = new Label();
        cInit.visitJumpInsn(Opcodes.GOTO, catchEndLabel);
        Label catchBeginLabel = new Label();
        cInit.visitLabel(catchBeginLabel);
        cInit.visitFrame(Opcodes.F_SAME1, 1, null, 1, new Object[]{"java/lang/Throwable"});
        cInit.visitVarInsn(Opcodes.ASTORE, 0);
        cInit.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
        cInit.visitInsn(Opcodes.DUP);
        cInit.visitVarInsn(Opcodes.ALOAD, 0);
        cInit.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false
        );
        cInit.visitInsn(Opcodes.ATHROW);
        cInit.visitLabel(catchEndLabel);
        cInit.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        cInit.visitInsn(Opcodes.RETURN);
        cInit.visitTryCatchBlock(
                tryBeginLabel,
                tryEndLabel,
                catchBeginLabel,
                "java/lang/Throwable"
        );
        cInit.visitMaxs(0, 0);
        cInit.visitEnd();
    }

    private static String getSyncFuncName(ExtOperand.OperandKey<Library.Function> key) {
        return FUNC_NAME_CACHE.getNameById(key.getId());
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
                        CONST_NAME_CACHE.getNameById(key.getId()),
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
                        ASYNC_CONST_NAME_CACHE.getNameById(key.getId()),
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
                        getSyncFuncName(key),
                        FUNC_TYPE_DESC
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
                        getAsyncFuncName(key),
                        ASYNC_FUNC_TYPE_DESC
                );
            }
        }
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        staticOperandFieldLength = initClzLocals;
    }

    private static String getAsyncFuncName(ExtOperand.OperandKey<Library.AsyncFunction> key) {
        return ASYNC_FUNC_NAME_CACHE.getNameById(key.getId());
    }

    private static String getLiteralVarName(ExtOperand.OperandKey<JsonNode> key) {
        return LITERAL_NAME_CACHE.getNameById(key.getId());
    }

    private void asmConstructor() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                CLZ_CONSTRUCTOR_DESC, null, null
        );
        methodVisitor.visitCode();
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
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void asmGetArgVal() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "getArgVal",
                CLZ_GET_ARG_VAL_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, SPREAD_NAME, "Z");
        Label elseLabel = new Label();
        methodVisitor.visitJumpInsn(Opcodes.IFNE, elseLabel);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        Label throwLabel = new Label();
        // var1 +
        methodVisitor.visitJumpInsn(Opcodes.IFLT, throwLabel);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, FUNC_ARGC_NAME, "I");
        Label getArgUnsafe = new Label();
        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPLT, getArgUnsafe);
        // throw new IllegalStateException("no arguments at :" + var0);

        methodVisitor.visitLabel(throwLabel);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/StringBuilder",
                "<init>",
                "()V",
                false
        );
        methodVisitor.visitLdcInsn("no arguments at :");
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false
        );
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(I)Ljava/lang/StringBuilder;",
                false
        );
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "toString",
                "()Ljava/lang/String;",
                false
        );
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/IllegalStateException",
                "<init>",
                "(Ljava/lang/String;)V",
                false
        );
        methodVisitor.visitInsn(Opcodes.ATHROW);
        methodVisitor.visitLabel(getArgUnsafe);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, internalClzName, STACK_OFFSET_NAME, "J");
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, internalClzName, JSON_NODE_OCCUPY_NAME, "J");
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, FUNC_PARAM_SP_NAME, "I");
        methodVisitor.visitInsn(Opcodes.IADD);
        methodVisitor.visitInsn(Opcodes.I2L);
        methodVisitor.visitInsn(Opcodes.LMUL);
        methodVisitor.visitInsn(Opcodes.LADD);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                UNSAFE_UNTIL_NAME,
                "getJsonNodeObject",
                "(Ljava/lang/Object;J)Lio/fiber/net/common/json/JsonNode;",
                false
        );
        methodVisitor.visitInsn(Opcodes.ARETURN);
        // else
        methodVisitor.visitLabel(elseLabel);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        unsafeGetFuncParamStack(methodVisitor);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                JSON_FIELD_TYPE_NAME,
                "get",
                "(I)Lio/fiber/net/common/json/JsonNode;",
                false
        );
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void asmGetArgCnt() {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "getArgCnt",
                CLZ_GET_ARG_CNT_DESC, null, null
        );
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, SPREAD_NAME, "Z");
        Label elseLabel = new Label();
        methodVisitor.visitJumpInsn(Opcodes.IFNE, elseLabel);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, FUNC_ARGC_NAME, "I");
        methodVisitor.visitInsn(Opcodes.IRETURN);

        methodVisitor.visitLabel(elseLabel);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        unsafeGetFuncParamStack(methodVisitor);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                JSON_FIELD_TYPE_NAME,
                "size",
                "()I",
                false
        );
        methodVisitor.visitInsn(Opcodes.IRETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void unsafeGetFuncParamStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, internalClzName, STACK_OFFSET_NAME, "J");
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, internalClzName, JSON_NODE_OCCUPY_NAME, "J");
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName, FUNC_PARAM_SP_NAME, "I");
        methodVisitor.visitInsn(Opcodes.I2L);
        methodVisitor.visitInsn(Opcodes.LMUL);
        methodVisitor.visitInsn(Opcodes.LADD);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                UNSAFE_UNTIL_NAME,
                "getJsonNodeObject",
                "(Ljava/lang/Object;J)Lio/fiber/net/common/json/JsonNode;",
                false
        );
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

    private static String[] initUnaryDESC() {
        String[] nameArr = new String[Unary.TYPES.length];
        for (Unary.Type type : Unary.TYPES) {
            String mtdName = type.name().toLowerCase();
            try {
                nameArr[type.ordinal()] = Type.getMethodDescriptor(Unaries.class.getDeclaredMethod(mtdName, JsonNode.class));
            } catch (NoSuchMethodException e) {
                e.printStackTrace(System.err);
                throw new IllegalStateException("change Binaries method??? ->" + mtdName, e);
            }
        }
        return nameArr;
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
        return STACK_NAME_CACHE.getNameById(sp);
    }


    public ClzAssembler(Compiled compiled) {
        this.compiled = compiled;
    }

    void stashStack(int sp) {
        Assert.isTrue(sp < compiled.getStackSize());
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitFieldInsn(Opcodes.PUTFIELD,
                internalClzName,
                getStackFieldName(sp),
                JSON_FIELD_TYPE_DESC
        );
    }

    void restoreStack(int sp) {
        Assert.isTrue(sp < compiled.getStackSize());
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD,
                internalClzName,
                getStackFieldName(sp),
                JSON_FIELD_TYPE_DESC
        );
    }

    void binary(Binary.Type type, boolean optimiseIF) {
        if (optimiseIF) {
            Assert.isTrue(type.ordinal() >= Binary.Type.MATCHES.ordinal());
            visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    COMPARES_NAME,
                    BINARY_MTD_NAMES[type.ordinal()],
                    "(Lio/fiber/net/common/json/JsonNode;Lio/fiber/net/common/json/JsonNode;)Z",
                    false
            );
            return;
        }

        String desc = type.ordinal() >= Binary.Type.MATCHES.ordinal() ? BINARY_BOOL_DESC : BINARY_JSON_DESC;
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                BINARIES_TYPE_NAME,
                BINARY_MTD_NAMES[type.ordinal()],
                desc,
                false
        );
    }

    void unary(Unary.Type type, boolean optimiseIF) {
        if (optimiseIF) {
            Assert.isTrue(type == Unary.Type.NEG);
            visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    COMPARES_NAME,
                    "neg",
                    "(Lio/fiber/net/common/json/JsonNode;)Z",
                    false
            );
            return;
        }
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                UNARIES_TYPE_NAME,
                UNARY_MTD_NAMES[type.ordinal()],
                UNARY_MTD_DESC[type.ordinal()],
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
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, "rtValue", JSON_FIELD_TYPE_DESC);
        return0();
    }

    void returnV() {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitInsn(Opcodes.ACONST_NULL);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, "rtValue", JSON_FIELD_TYPE_DESC);
        return0();
    }

    private void return0() {
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


    void visitLabel(Label label) {
        visitor.visitLabel(label);
    }

    void asyncFuncCall(FunctionCall fc) {
        prepareFuncCall(fc.getRestoreStackSize(), fc.isSpread() ? -1 : fc.getArgCount());

        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETSTATIC,
                internalClzName,
                getAsyncFuncName(asyncFunctionExtOperand.getKeyList().get(fc.getFuncId())),
                ASYNC_FUNC_TYPE_DESC
        );
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                internalClzName,
                "callAsyncFunc",
                "(Lio/fiber/net/script/Library$AsyncFunction;)Z",
                false
        );
        checkAsyncStateAndRestoreStack(fc.getRestoreStackSize(), fc.getAsyncPoint(), fc.getDist());
    }

    private void checkAsyncStateAndRestoreStack(int restoreStackSize, int asyncPointId, ResDist dist) {
        Label returnLabel = new Label();
        visitor.visitJumpInsn(Opcodes.IFEQ, returnLabel);
        // if() { asyncState = 1; return; }

        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        constBiPush(visitor, asyncPointId + 1);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, ASYNC_STATE_NAME, "I");
        visitor.visitInsn(Opcodes.RETURN);

        // state = STAT_RUNNING;
        visitor.visitLabel(returnLabel);
        visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        constBiPush(visitor, AbstractVm.STAT_RUNNING);
        visitor.visitFieldInsn(Opcodes.PUTFIELD,
                internalClzName,
                "state",
                "I"
        );
        // case asyncPointId+1:
        Label asyncLabel = asyncPoint.getKeyList().get(asyncPointId).getKey();
        visitor.visitLabel(asyncLabel);
        visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

        // if(rtError != null) {throw rtError;}

        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD,
                internalClzName,
                "rtError",
                SCRIPT_EXEC_DESC
        );

        Label success = new Label();
        visitor.visitJumpInsn(Opcodes.IFNULL, success);
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD,
                internalClzName,
                "rtError",
                SCRIPT_EXEC_DESC
        );
        visitor.visitInsn(Opcodes.ATHROW);

        visitor.visitLabel(success);
        visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);


        // restore stack
        for (int i = 0; i < restoreStackSize; i++) {
            restoreStack(i);
        }

        if (dist != ResDist.POP) {
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD,
                    internalClzName,
                    "rtValue",
                    JSON_FIELD_TYPE_DESC
            );
        }
    }

    private void prepareFuncCall(int paramSP, int argc) {
        //
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        constBiPush(visitor, paramSP);
        visitor.visitFieldInsn(Opcodes.PUTFIELD,
                internalClzName,
                FUNC_PARAM_SP_NAME,
                "I"
        );
        if (argc < 0) {
            // spread = true;
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            constBiPush(visitor, 1);
            visitor.visitFieldInsn(Opcodes.PUTFIELD,
                    internalClzName,
                    SPREAD_NAME,
                    "Z"
            );
        } else {
            // funcArgc = argc;
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            constBiPush(visitor, argc);
            visitor.visitFieldInsn(Opcodes.PUTFIELD,
                    internalClzName,
                    FUNC_ARGC_NAME,
                    "I"
            );
            // spread = false;
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            constBiPush(visitor, 0);
            visitor.visitFieldInsn(Opcodes.PUTFIELD,
                    internalClzName,
                    SPREAD_NAME,
                    "Z"
            );
        }
    }

    void syncFuncCall(FunctionCall fc) {
        prepareFuncCall(0, fc.isSpread() ? -1 : fc.getArgCount());
        visitor.visitFieldInsn(Opcodes.GETSTATIC,
                internalClzName,
                getSyncFuncName(functionExtOperand.getKeyList().get(fc.getFuncId())),
                FUNC_TYPE_DESC
        );
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                FUNC_TYPE_NAME,
                "call",
                "(Lio/fiber/net/script/ExecutionContext;)Lio/fiber/net/common/json/JsonNode;",
                true
        );
        if (fc.getDist() != ResDist.POP) {
            visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    internalClzName,
                    "nullNode",
                    "(Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/JsonNode;",
                    false
            );
        } else {
            pop();
        }
    }


    void asyncConstCall(ConstCall constCall) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETSTATIC,
                internalClzName,
                getAsyncConstName(asyncConstantExtOperand.getKeyList().get(constCall.getConstId())),
                ASYNC_CONST_FIELD_TYPE_DESC
        );
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                internalClzName,
                "callAsyncConst",
                "(" + ASYNC_CONST_FIELD_TYPE_DESC + ")Z",
                false
        );
        checkAsyncStateAndRestoreStack(constCall.getPrevStackSize(), constCall.getAsyncPoint(), constCall.getDist());
    }

    private String getAsyncConstName(ExtOperand.OperandKey<Library.AsyncConstant> key) {
        return ASYNC_CONST_NAME_CACHE.getNameById(key.getId());
    }

    void constCall(ConstCall cc) {
        visitor.visitFieldInsn(Opcodes.GETSTATIC,
                internalClzName,
                CONST_NAME_CACHE.getNameById(functionExtOperand.getKeyList().get(cc.getConstId()).getId()),
                CONSTANT_FIELD_TYPE_DESC
        );
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                FUNC_TYPE_NAME,
                "get",
                "(Lio/fiber/net/script/ExecutionContext;)Lio/fiber/net/common/json/JsonNode;",
                true
        );
        if (cc.getDist() != ResDist.POP) {
            visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    internalClzName,
                    "nullNode",
                    "(Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/JsonNode;",
                    false
            );
        } else {
            pop();
        }
    }

    void loadVar(VarTable.VarDef loadVar) {
        if (loadVar.isAsync()) {
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD, internalClzName,
                    ASYNC_VAR_NAME_CACHE.getNameById(loadVar.getGlobalIdx()),
                    JSON_FIELD_TYPE_DESC
            );
        } else {
            visitor.visitVarInsn(Opcodes.ALOAD, loadVar.getGlobalIdx() + 1);
        }
    }

    void storeVar(VarTable.VarDef storeVar) {
        if (storeVar.isAsync()) {
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitInsn(Opcodes.SWAP);
            visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName,
                    ASYNC_VAR_NAME_CACHE.getNameById(storeVar.getGlobalIdx()),
                    JSON_FIELD_TYPE_DESC
            );
        } else {
            visitor.visitVarInsn(Opcodes.ASTORE, storeVar.getGlobalIdx() + 1);
        }
    }

    void conditionalJump(ConditionalJump conditionalJump) {
        if (!conditionalJump.isOptimiseIf()) {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                    COMPARES_NAME,
                    "logic",
                    "(Lio/fiber/net/common/json/JsonNode;)Z",
                    false
            );
        }
        visitor.visitJumpInsn(conditionalJump.isTrueJump() ? Opcodes.IFNE : Opcodes.IFEQ,
                conditionalJump.getTarget().getStartLabel());
    }

    void jump(CodeEnterPoint target) {
        visitor.visitJumpInsn(Opcodes.GOTO, target.getStartLabel());
    }

    void iterateInto(VarTable.VarDef storeVar) {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                UNARIES_TYPE_NAME,
                "iterate",
                "(Lio/fiber/net/common/json/JsonNode;)Lio/fiber/net/common/json/IteratorNode;",
                false
        );
        storeVar(storeVar);
    }

    void iterateNext(VarTable.VarDef loadVar, boolean optimiseIf) {
        loadIterator(loadVar);
        visitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                JSON_ITERATOR_TYPE_NAME,
                "next",
                "()Z",
                false
        );
        if (optimiseIf) {
            return;
        }
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                JSON_BOOLEAN_TYPE_NAME,
                "valueOf",
                "(Z)Lio/fiber/net/common/json/BooleanNode;",
                false
        );
    }

    private void loadIterator(VarTable.VarDef loadVar) {
        loadVar(loadVar);
        visitor.visitTypeInsn(Opcodes.CHECKCAST, JSON_ITERATOR_TYPE_NAME);
    }

    void iterateVar(IterateVar iterateVar) {
        loadIterator(iterateVar.getLoadVar());
        visitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                JSON_ITERATOR_TYPE_NAME,
                iterateVar.isKey() ? "currentKey" : "currentValue",
                "()" + JSON_FIELD_TYPE_DESC,
                false
        );
        storeVar(iterateVar.getStoreVar());
    }

    void intoCatch(VarTable.VarDef storeVar) {
        // rtError = e;
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, internalClzName, "rtError", SCRIPT_EXEC_DESC);

        // e = errorToObj();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                internalClzName,
                "errorToObj",
                "()" + JSON_FIELD_TYPE_DESC,
                false
        );
        storeVar(storeVar);
    }

    void throwExp() {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                internalClzName,
                "objToError",
                "(" + JSON_FIELD_TYPE_DESC + ")" + SCRIPT_EXEC_DESC,
                false
        );
        visitor.visitInsn(Opcodes.ATHROW);
    }


    void writeFrame(VarTable vt, boolean catchPoint) {
        VarTable pt = vt.getPrevTableForFrame();
        int local = vt.getUseOuterSyncSize();
        int stack = vt.getStackSizeForFrame();
        Assert.isTrue(local >= 0 && stack >= 0);


        if (catchPoint) {
            Assert.isTrue(stack == 0, "catchPoint not zero???");
            stack = 1;

            if (local == 0) {
                visitor.visitFrame(Opcodes.F_SAME1, 0, null, stack, STACK_EXCEPTION_TYPE);
            } else {
                Object[] localStack = STACK_LOCAL_TYPE;
                if (local > STACK_LOCAL_TYPE.length) {
                    localStack = Arrays.copyOf(STACK_LOCAL_TYPE, local);
                    for (int i = STACK_LOCAL_TYPE.length; i < localStack.length; i++) {
                        localStack[i] = JSON_FIELD_TYPE_NAME;
                    }
                }
                visitor.visitFrame(Opcodes.F_FULL, local, localStack, stack, STACK_EXCEPTION_TYPE);
            }
            return;
        }

        if (pt != null) {
            local -= pt.getUseOuterSyncSize();
        }

        if (local == 0 && stack <= 1) {
            visitor.visitFrame(stack == 0 ? Opcodes.F_SAME : Opcodes.F_SAME1, 0, null, stack, STACK_LOCAL_TYPE);
        } else if (Math.abs(local) <= 3 && stack == 0) {
            visitor.visitFrame(local > 0 ? Opcodes.F_APPEND : Opcodes.F_CHOP, Math.abs(local), STACK_LOCAL_TYPE, 0, null);
        } else {
            local = vt.getUseOuterSyncSize();

            Object[] localStack = STACK_LOCAL_TYPE;
            if (Math.max(local, stack) > STACK_LOCAL_TYPE.length) {
                localStack = Arrays.copyOf(STACK_LOCAL_TYPE, Math.max(local, stack));
                for (int i = STACK_LOCAL_TYPE.length; i < localStack.length; i++) {
                    localStack[i] = JSON_FIELD_TYPE_NAME;
                }
            }

            visitor.visitFrame(Opcodes.F_FULL, local, localStack, stack, localStack);
        }
    }


}
