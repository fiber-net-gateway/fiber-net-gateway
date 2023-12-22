package io.fiber.net.proxy.lib;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.std.StdLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtensiveHttpLib extends StdLibrary {
    final Injector injector;
    final HttpLibConfigure[] configures;

    private static Map<String, Function> getFuncMap() {
        HashMap<String, Function> map = new HashMap<>();
        map.putAll(StdLibrary.getDefFuncMap());
        map.putAll(ReqFunc.FC_MAP);
        map.putAll(RespFunc.FC_MAP);
        return map;
    }

    public ExtensiveHttpLib(Injector injector, HttpLibConfigure[] configures) {
        super(getFuncMap());
        this.injector = injector;
        this.configures = configures;
    }

    public void registerFunc(String name, Function fc, boolean override) {
        if (override) {
            functionMap.put(name, fc);
        } else {
            functionMap.putIfAbsent(name, fc);
        }
    }

    @Override
    public Constant findConstant(String namespace, String key) {
        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                Constant def = configure.findConst(namespace, key);
                if (def != null) {
                    return def;
                }
            }
        }
        return null;
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        if (ArrayUtils.isNotEmpty(configures)) {
            for (HttpLibConfigure configure : configures) {
                DirectiveDef def = configure.findDirectiveDef(type, name, literals);
                if (def != null) {
                    return def;
                }
            }
        }
        if ("http".equals(type)) {
            HttpHost httpHost = HttpHost.create(literals.get(0).getLiteralValue().textValue());
            return new HttpFunc(httpHost, injector.getInstance(HttpClient.class));
        }
        return null;
    }

}