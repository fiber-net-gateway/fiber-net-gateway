package lua.test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

public class LoaderTest {

    static class Loader extends ClassLoader {
        private byte[] arr;
        private final String name;

        Loader(String name, byte[] arr) {
            super(Loader.class.getClassLoader());
            this.arr = arr;
            this.name = name;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(this.name)) {
                Class<?> clz = defineClass(name, arr, 0, arr.length);
                arr = null;
                return clz;
            }
            throw new ClassNotFoundException("load other class is not allowed:" + name);
        }

        static Class<?> loadClz(String name, byte[] data) throws ClassNotFoundException {
            Loader loader = new Loader(name, data);
            return loader.loadClass(name);
        }
    }


    public static void main(String[] args) throws Exception {
        File file = new File("/Users/eleme/IdeaProjects/fiber-net-gateway/fiber-gateway-script/dist/io/fiber/net/script/run/GeneratedVm_0.class");
        byte[] bytes = Files.readAllBytes(file.toPath());
        Class<?> clz = Loader.loadClz("io.fiber.net.script.run.GeneratedVm_0", bytes);

        Method method = clz.getDeclaredMethod("__INIT_OPERAND__", Object[].class);
        method.invoke(null, (Object) null);

        System.out.println(clz);
    }
}
