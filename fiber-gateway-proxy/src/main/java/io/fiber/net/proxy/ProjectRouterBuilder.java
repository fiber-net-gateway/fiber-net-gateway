package io.fiber.net.proxy;

import io.fiber.net.common.ioc.Injector;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Script;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ProjectRouterBuilder {
    private final Injector injector;
    private File file;
    private String name;

    public ProjectRouterBuilder(Injector injector) {
        this.injector = injector;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFile(File file) {
        this.file = file;
    }


    private Script parseScript() throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        ExtensiveHttpLib library = new ExtensiveHttpLib(injector);
        return Script.compile(new String(bytes, StandardCharsets.UTF_8), library);
    }

    public ScriptProjectRouter build() throws Exception {
        Script script = parseScript();
        ScriptProjectRouter router = new ScriptProjectRouter(injector, name);
        router.setScript(script);
        return router;
    }


}
