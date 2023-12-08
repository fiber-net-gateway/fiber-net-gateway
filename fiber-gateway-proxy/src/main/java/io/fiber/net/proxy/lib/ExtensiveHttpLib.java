package io.fiber.net.proxy.lib;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Predictions;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.std.StdLibrary;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtensiveHttpLib extends StdLibrary {
    final Injector injector;

    private static Map<String, Function> getFuncMap() {
        HashMap<String, Function> map = new HashMap<>();
        map.putAll(StdLibrary.getDefFuncMap());
        map.putAll(ReqFunc.FC_MAP);
        map.putAll(RespFunc.FC_MAP);
        return map;
    }

    public ExtensiveHttpLib(Injector injector) {
        super(getFuncMap(), new HashMap<>());
        this.injector = injector;
    }

    @Override
    public DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
        Predictions.assertTrue("http".equals(type), "only support http directive");
        HttpHost httpHost = HttpHost.create(literals.get(0).getLiteralValue().textValue());
        return new HttpFunc(httpHost, injector.getInstance(HttpClient.class));
    }

}