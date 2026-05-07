package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.parse.Compiled;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class AotSsaClassGenerator implements Opcodes {
    private static final AtomicLong ID = new AtomicLong();
    private static final String CLZ_PREFIX = "io/fiber/net/script/aot/GeneratedSsaAotVm_";
    private static final String INIT_PLAN_NAME = "__INIT_AOT_PLAN__";
    private static final String JSON_NODE_DESC = Type.getDescriptor(JsonNode.class);
    private static final String EMITTER_DESC = Type.getDescriptor(Maybe.Emitter.class);
    private static final String PLAN_DESC = Type.getDescriptor(AotRuntimePlan.class);
    private static final String RUNTIME_NAME = Type.getInternalName(AotRuntimeVm.class);
    private static final String CONSTRUCTOR_DESC = '(' + JSON_NODE_DESC + "Ljava/lang/Object;" + EMITTER_DESC + ")V";
    private static final String RUNTIME_CONSTRUCTOR_DESC = '(' + JSON_NODE_DESC
            + "Ljava/lang/Object;" + EMITTER_DESC + PLAN_DESC + ")V";
    private static final String INIT_PLAN_DESC = '(' + PLAN_DESC + ")V";

    private final Compiled compiled;
    private final String internalClassName;
    private byte[] classData;
    private AotRuntimePlan runtimePlan;

    public AotSsaClassGenerator(Compiled compiled) {
        if (compiled == null) {
            throw new IllegalArgumentException("compiled is null");
        }
        this.compiled = compiled;
        this.internalClassName = CLZ_PREFIX + ID.incrementAndGet();
    }

    public String getGeneratedClzName() {
        return internalClassName;
    }

    public byte[] generateClzData() {
        if (classData == null) {
            runtimePlan = buildRuntimePlan();
            classData = asmClass();
        }
        return classData.clone();
    }

    @SuppressWarnings("unchecked")
    public Class<? extends AotRuntimeVm> loadAsClass() throws Throwable {
        byte[] bytes = generateClzData();
        String className = internalClassName.replace('/', '.');
        Class<?> clazz = AotSsaGeneratedClassLoader.loadClass(className, bytes);
        Method method = clazz.getDeclaredMethod(INIT_PLAN_NAME, AotRuntimePlan.class);
        method.invoke(null, runtimePlan);
        return (Class<? extends AotRuntimeVm>) clazz;
    }

    public AotRuntimePlan runtimePlan() {
        if (runtimePlan == null) {
            runtimePlan = buildRuntimePlan();
        }
        return runtimePlan;
    }

    private AotRuntimePlan buildRuntimePlan() {
        Cfg cfg = new AotCfgBuilder().build(compiled);
        CfgOptimizer cfgOptimizer = cfgOptimizer();
        cfg = cfgOptimizer.optimize(cfg);

        for (int i = 0; i < 4; i++) {
            SsaGraph graph = new SsaBuilder().build(cfg, compiled.getVarTableSize(), compiled.getOperands());
            SsaOptimizer.defaults().optimize(graph);
            Cfg threaded = new JumpThreadingPass().run(cfg, graph);
            if (threaded == cfg) {
                break;
            }
            cfg = cfgOptimizer.optimize(threaded);
        }

        CompactBytecode compact = new CfgCompactor().compact(cfg);
        int[] codes = compact.codes();
        long[] pos = remapPositions(compact, codes.length);
        return new AotRuntimePlan(compiled.getStackSize(), compiled.getVarTableSize(), codes, compiled.getOperands(), pos,
                compact.exceptionTable());
    }

    private CfgOptimizer cfgOptimizer() {
        return new CfgOptimizer(Arrays.asList(
                new RemoveUnreachableBlocksPass(),
                new BranchSimplificationPass(compiled.getOperands()),
                new BasicBlockMergePass(),
                new RemoveUnreachableBlocksPass()
        ));
    }

    private long[] remapPositions(CompactBytecode compact, int newLength) {
        long[] oldPos = compiled.getPos();
        long[] newPos = new long[newLength];
        boolean[] filled = new boolean[newLength];
        if (oldPos != null) {
            for (Map.Entry<Integer, Integer> entry : compact.oldPcToNewPc().entrySet()) {
                int oldPc = entry.getKey();
                int newPc = entry.getValue();
                if (oldPc >= 0 && oldPc < oldPos.length && newPc >= 0 && newPc < newLength) {
                    newPos[newPc] = oldPos[oldPc];
                    filled[newPc] = true;
                }
            }
        }
        long last = oldPos == null || oldPos.length == 0 ? 0 : oldPos[0];
        for (int i = 0; i < newPos.length; i++) {
            if (filled[i]) {
                last = newPos[i];
            } else {
                newPos[i] = last;
            }
        }
        return newPos;
    }

    private byte[] asmClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, internalClassName, null, RUNTIME_NAME, null);
        writer.visitField(ACC_PRIVATE | ACC_STATIC, "PLAN", PLAN_DESC, null, null).visitEnd();
        asmConstructor(writer);
        asmInitPlan(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void asmConstructor(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, "<init>", CONSTRUCTOR_DESC, null, null);
        visitor.visitCode();
        visitor.visitVarInsn(ALOAD, 0);
        visitor.visitVarInsn(ALOAD, 1);
        visitor.visitVarInsn(ALOAD, 2);
        visitor.visitVarInsn(ALOAD, 3);
        visitor.visitFieldInsn(GETSTATIC, internalClassName, "PLAN", PLAN_DESC);
        visitor.visitMethodInsn(INVOKESPECIAL, RUNTIME_NAME, "<init>", RUNTIME_CONSTRUCTOR_DESC, false);
        visitor.visitInsn(RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private void asmInitPlan(ClassWriter writer) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, INIT_PLAN_NAME, INIT_PLAN_DESC, null, null);
        visitor.visitCode();
        visitor.visitVarInsn(ALOAD, 0);
        visitor.visitFieldInsn(PUTSTATIC, internalClassName, "PLAN", PLAN_DESC);
        visitor.visitInsn(RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }
}
