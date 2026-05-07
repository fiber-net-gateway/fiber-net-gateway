package io.fiber.net.script.aot;

final class AotSsaGeneratedClassLoader extends ClassLoader {
    private final String className;
    private final byte[] classData;

    private AotSsaGeneratedClassLoader(String className, byte[] classData) {
        super(AotRuntimeVm.class.getClassLoader());
        this.className = className;
        this.classData = classData;
    }

    static Class<?> loadClass(String className, byte[] classData) throws ClassNotFoundException {
        return new AotSsaGeneratedClassLoader(className, classData).loadClass(className);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!className.equals(name)) {
            return super.findClass(name);
        }
        return defineClass(name, classData, 0, classData.length);
    }
}
