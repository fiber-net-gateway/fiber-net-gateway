package io.fiber.net.script.aot;

import io.fiber.net.script.run.AbstractVm;

class CfgAotGeneratedClassLoader extends ClassLoader {
    private byte[] data;
    private final String name;

    private CfgAotGeneratedClassLoader(String name, byte[] data) {
        super(AbstractVm.class.getClassLoader());
        this.name = name;
        this.data = data;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (this.name.equals(name)) {
            Class<?> clz = defineClass(name, data, 0, data.length);
            data = null;
            return clz;
        }
        throw new ClassNotFoundException("load other class is not allowed:" + name);
    }

    static Class<?> loadClz(String name, byte[] data) throws ClassNotFoundException {
        return new CfgAotGeneratedClassLoader(name, data).loadClass(name);
    }
}
