package io.fiber.net.proxy;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Script;

public class ProjectRouterBuilder {
    private final Injector injector;
    private String code;
    private String name;

    public ProjectRouterBuilder(Injector injector) {
        this.injector = injector;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    private Script parseScript() throws Exception {
        Assert.isTrue(code != null);
        HttpLibConfigure[] configures = injector.getInstances(HttpLibConfigure.class);
        ExtensiveHttpLib library = new ExtensiveHttpLib(injector, configures);
        if (configures != null) {
            for (HttpLibConfigure configure : configures) {
                configure.onInit(library);
            }
        }
        Script compiled = Script.compile(code, library);
        code = null;
        return compiled;
    }

    public ScriptHandler build() throws Exception {
        Script script = parseScript();
        ScriptHandler router = new ScriptHandler(injector, name);
        router.setScript(script);
        return router;
    }


}
