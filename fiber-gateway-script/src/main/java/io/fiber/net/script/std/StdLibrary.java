package io.fiber.net.script.std;

import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StdLibrary implements Library {
    private static final Map<String, Library.Function> DEF_FUNC_MAP = new HashMap<>();

    static {
        DEF_FUNC_MAP.put("random", new RandomFunc());
        DEF_FUNC_MAP.put("now", new NowFunc());
        DEF_FUNC_MAP.put("canary", new CanaryFunc());
        DEF_FUNC_MAP.put("crc32", new Crc32Func());
        DEF_FUNC_MAP.put("includes", new IncludesFunc());
        DEF_FUNC_MAP.put("length", LengthFunc.INSTANCE);
        DEF_FUNC_MAP.putAll(ArrayFuncs.FUNC);
        DEF_FUNC_MAP.putAll(ObjectsFuncs.FUNC);
        DEF_FUNC_MAP.putAll(StringsFuncs.FUNC);
        DEF_FUNC_MAP.putAll(JsonFunc.FUNC);
        DEF_FUNC_MAP.putAll(MathFuncs.FUNC);
        DEF_FUNC_MAP.putAll(BinaryFunc.FUNC);
    }

    protected static Map<String, Library.Function> getDefFuncMap() {
        return DEF_FUNC_MAP;
    }

    private final static StdLibrary DEF_INSTANCE = new StdLibrary();

    public static StdLibrary getDefInstance() {
        return DEF_INSTANCE;
    }

    protected final Map<String, Object> functionMap = new HashMap<>();
    protected final Map<String, Object> constantMap = new HashMap<>();

    public StdLibrary() {
        functionMap.putAll(DEF_FUNC_MAP);
    }

    public void putFunc(String name, Function function) {
        Object old = functionMap.put(name, Objects.requireNonNull(function));
        if (old != null) {
            throw new IllegalStateException("function " + name + " exists");
        }
    }

    public void putAsyncFunc(String name, AsyncFunction function) {
        Object old = functionMap.put(name, Objects.requireNonNull(function));
        if (old != null) {
            throw new IllegalStateException("function " + name + " exists");
        }
    }

    public void putConstant(String namespace, String key, Constant constant) {
        String name = constantName(namespace, key);
        Object old = constantMap.put(name, Objects.requireNonNull(constant));
        if (old != null) {
            throw new IllegalStateException("constant " + name + " exists");
        }
    }

    public void putAsyncConstant(String namespace, String key, AsyncConstant constant) {
        String name = constantName(namespace, key);
        Object old = constantMap.put(name, Objects.requireNonNull(constant));
        if (old != null) {
            throw new IllegalStateException("function " + name + " exists");
        }
    }

    private static String constantName(String namespace, String key) {
        return namespace + "/" + key;
    }

    @Override
    public Object findFunc(String name) {
        return functionMap.get(name);
    }

    @Override
    public Object findConstant(String namespace, String key) {
        return constantMap.get(constantName(namespace, key));
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        return null;
    }
}
