package lua.test;

import org.objectweb.asm.*;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;

public class ClazzTest {
    public static void main(String[] args) throws Exception {
        System.out.println(Type.getDescriptor(PrintStream.class));

        ClassWriter writer = new ClassWriter(0);

        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                "a/User",
                null,
                "lua/test/ClazzTest",
                new String[]{}
        );
        {
            MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                    "()V", null, null
            );
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(10, label0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "lua/test/ClazzTest", "<init>",
                    "()V", false);


            methodVisitor.visitIntInsn(Opcodes.SIPUSH, 2000);
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, 3000);
            methodVisitor.visitInsn(Opcodes.IADD);
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, 3000);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 2);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 1);


            methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
            methodVisitor.visitInsn(Opcodes.IADD);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 1);

            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                    "(I)V",
                    false);


            {
                Label c0 = new Label();
                Label c1 = new Label();
                Label c2 = new Label();
                Label def = new Label();
                methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
                methodVisitor.visitTableSwitchInsn(0, 2, def, c0, c1, c2);

                methodVisitor.visitLabel(c0);
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                        "(I)V",
                        false);

                methodVisitor.visitLabel(c1);
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                        "(I)V",
                        false);

                methodVisitor.visitLabel(c2);
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                methodVisitor.visitInsn(Opcodes.ICONST_0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                        "(I)V",
                        false);


                methodVisitor.visitLabel(def);
            }


            methodVisitor.visitInsn(Opcodes.RETURN);

            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "La/User", null, label0, label1, 0);
            methodVisitor.visitLocalVariable("a", "I", null, label0, label1, 1);
            methodVisitor.visitLocalVariable("b", "I", null, label0, label1, 2);
            methodVisitor.visitMaxs(7, 3);
            methodVisitor.visitEnd();
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        File path = new File("dist/a");
        path.mkdirs();
        Files.write(new File(path, "User.class").toPath(), bytes);

    }


}
