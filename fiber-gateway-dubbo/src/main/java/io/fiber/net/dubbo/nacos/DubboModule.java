package io.fiber.net.dubbo.nacos;

import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Module;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.ProxyModule;

public class DubboModule implements Module {
    private static DubboConfig defaultConfig() {
        String registry = SystemPropertyUtil.get("fiber.dubbo.registry");
        if (StringUtils.isEmpty(registry)) {
            throw new IllegalStateException("no dubbo registry configured. -Dfiber.dubbo.registry=");
        }
        DubboConfig config = new DubboConfig();
        config.setRegistryAddr(registry);
        config.setApplicationName(SystemPropertyUtil.get("fiber.dubbo.appName", config.getApplicationName()));
        config.setProtocol(SystemPropertyUtil.get("fiber.dubbo.protocol", config.getProtocol()));
        return config;
    }

    public static void install(Binder binder, DubboConfig config) {
        binder.bindFactory(DubboClient.class, injector -> new DubboClient(config == null ? defaultConfig() : config));
        binder.bindMultiBean(ProxyModule.class, ScriptModule.class);
        binder.bind(ScriptModule.class, ScriptModule.INSTANCE);
    }

    private final DubboConfig config;

    public DubboModule() {
        this(null);
    }

    public DubboModule(DubboConfig config) {
        this.config = config;
    }

    @Override
    public void install(Binder binder) {
        install(binder, config);
    }


    private static class ScriptModule implements ProxyModule {
        static final ScriptModule INSTANCE = new ScriptModule();

        @Override
        public void install(Binder binder) {
            binder.bindPrototype(DubboLibConfigure.class, DubboLibConfigure::new);
            binder.bindMultiBean(HttpLibConfigure.class, DubboLibConfigure.class);
            binder.bindFactory(DubboRefManager.class, DubboRefManager::new);
        }
    }
}
