package lua.test;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Library;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.Binaries;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.lang.reflect.Method;
import java.util.List;

public class Signature {
    public static void main(String[] args) throws Exception {
        Class<MyGeneratedVm> clazz = MyGeneratedVm.class;
        System.out.println(Type.getConstructorDescriptor(clazz.getConstructor(JsonNode.class, Object.class)));
        System.out.println(Type.getMethodDescriptor(clazz.getMethod("getArgCnt")));
        System.out.println(Type.getMethodDescriptor(clazz.getMethod("getArgVal", int.class)));
        Type type = Type.getType(clazz.getMethod("exec"));
        System.out.println(type.getDescriptor());
        SignatureWriter signatureWriter = new SignatureWriter();
        signatureWriter.visitClassType(type.getInternalName());

//        System.out.println(Type.getType(clazz.getMethod("exec")).getReturnType().);

        {
            SignatureVisitor sv = new SignatureWriter();
            SignatureVisitor psv = sv.visitParameterType();
            psv.visitClassType(Type.getInternalName(List.class));
            SignatureVisitor ppsv = psv.visitTypeArgument('=');
            ppsv.visitClassType(Type.getInternalName(String.class));
            psv.visitEnd();
            SignatureVisitor rtv = sv.visitReturnType();
            rtv.visitBaseType('V');
            String signature = sv.toString();
            System.out.println(signature);
        }

        System.out.println(Type.getInternalName(JsonNode.class));
        System.out.println(Type.getMethodDescriptor(Binaries.class
                .getDeclaredMethod("matches", JsonNode.class, JsonNode.class)));

        System.out.println(Type.getMethodDescriptor(JsonNode.class
                .getDeclaredMethod("deepCopy")));

        Method method = AbstractVm.class.getDeclaredMethod("callAsyncFunc", Library.AsyncFunction.class);
        System.out.println(Type.getMethodDescriptor(method));

    }
}
