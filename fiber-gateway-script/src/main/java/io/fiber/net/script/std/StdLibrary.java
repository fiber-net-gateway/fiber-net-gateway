package io.fiber.net.script.std;

import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StdLibrary implements Library {
    private static final Map<String, Library.Function> DEF_FUNC_MAP = new HashMap<>();

    static {
        DEF_FUNC_MAP.put("random", new RandomFunc());
        DEF_FUNC_MAP.put("now", new NowFunc());
        DEF_FUNC_MAP.put("canary", new CanaryFunc());
        DEF_FUNC_MAP.put("crc32", new Crc32Func());
        DEF_FUNC_MAP.put("includes", new IncludesFunc());
        DEF_FUNC_MAP.putAll(ArrayFuncs.FUNC);
        DEF_FUNC_MAP.putAll(ObjectsFuncs.FUNC);
        DEF_FUNC_MAP.putAll(StringsFuncs.FUNC);
        DEF_FUNC_MAP.putAll(MathFuncs.FUNC);
        DEF_FUNC_MAP.putAll(BinaryFunc.FUNC);
    }

    public static Map<String, Library.Function> getDefFuncMap() {
        return DEF_FUNC_MAP;
    }

    private final static StdLibrary DEF_INSTANCE = new StdLibrary(DEF_FUNC_MAP, Collections.emptyMap());

    public static StdLibrary getDefInstance() {
        return DEF_INSTANCE;
    }

    protected final Map<String, Library.Function> functionMap;
    protected final Map<String, Map<String, Constant>> constantMap;

    public StdLibrary(Map<String, Library.Function> functionMap,
                      Map<String, Map<String, Constant>> constantMap) {
        this.functionMap = functionMap;
        this.constantMap = constantMap;
    }

    @Override
    public Library.Function findFunc(String name) {
        return functionMap.get(name);
    }

    @Override
    public Constant findConstant(String namespace, String key) {
        return constantMap.get(namespace).get(key);
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        return null;
    }
}
