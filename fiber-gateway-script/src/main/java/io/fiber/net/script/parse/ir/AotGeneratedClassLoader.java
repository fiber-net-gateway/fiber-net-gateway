package io.fiber.net.script.parse.ir;

import io.fiber.net.script.run.AbstractVm;

class AotGeneratedClassLoader extends ClassLoader {
    private byte[] arr;
    private final String name;

    AotGeneratedClassLoader(String name, byte[] arr) {
        super(AbstractVm.class.getClassLoader());
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

    public static Class<?> loadClz(String name, byte[] data) throws ClassNotFoundException {
        AotGeneratedClassLoader loader = new AotGeneratedClassLoader(name, data);
        return loader.loadClass(name);
    }
}