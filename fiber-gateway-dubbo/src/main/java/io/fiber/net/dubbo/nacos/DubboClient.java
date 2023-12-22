package io.fiber.net.dubbo.nacos;

import io.fiber.net.common.utils.StringUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class DubboClient {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(DubboClient.class);
    private final Map<String, Service> map = new ConcurrentHashMap<>();
    private final ApplicationModel model;

    public DubboClient(DubboConfig dubboConfig) {
        ApplicationConfig config = new ApplicationConfig(dubboConfig.getApplicationName());
        ApplicationModel model = ApplicationModel.defaultModel();
        model.getApplicationConfigManager().setApplication(config);

        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress(dubboConfig.getRegistryAddr());
        model.getApplicationConfigManager().addRegistry(registryConfig);

        if (StringUtils.isNotEmpty(dubboConfig.getProtocol())) {
            ProtocolConfig protocolConfig = new ProtocolConfig();
            protocolConfig.setName("dubbo");
            model.getApplicationConfigManager().addProtocol(protocolConfig);
        }

        this.model = model;
    }

    private Service createService(String interfaceName) {
        return Service.create(interfaceName, this);
    }

    public DubboReference getRef(String name, long timeout) {
        while (true) {
            Service service = map.computeIfAbsent(name, this::createService);
            if (service.addRef()) {
                return new DubboReference(service, timeout);
            }
            if (map.remove(name, service)) {
                service.referenceConfig.destroy();
                log.info("destroy dubbo service({})", name);
            }
        }
    }

    static class Service {
        private static final AtomicIntegerFieldUpdater<Service> UPDATER
                = AtomicIntegerFieldUpdater.newUpdater(Service.class, "refCount");
        final GenericService genericService;
        private final DubboClient dubboClient;
        private final ReferenceConfig<Object> referenceConfig;
        private Thread creator = Thread.currentThread();
        private volatile int refCount = 1;

        private Service(GenericService genericService, DubboClient dubboClient,
                        ReferenceConfig<Object> referenceConfig) {
            this.genericService = genericService;
            this.dubboClient = dubboClient;
            this.referenceConfig = referenceConfig;
        }

        private static Service create(String interfaceName, DubboClient dubboClient) {
            ReferenceConfig<Object> referenceConfig = new ReferenceConfig<>(dubboClient.model.getDefaultModule());
            referenceConfig.setInterface(GenericService.class);
            referenceConfig.setGeneric("true");
            referenceConfig.setServices(interfaceName);
            referenceConfig.setInterface(interfaceName);
            return new Service((GenericService) referenceConfig.get(false), dubboClient, referenceConfig);
        }

        private boolean addRef() {
            if (creator != null && Thread.currentThread() == creator) {
                creator = null;
                return true;
            }
            int c;
            do {
                c = refCount;
                if (c <= 0) {
                    log.warn("dubbo service ({}) is destroyed?", referenceConfig.getInterface());
                    return false;
                }
            } while (!UPDATER.compareAndSet(this, c, c + 1));
            return true;
        }

        void destroy() {
            int c;
            do {
                c = refCount;
                if (c <= 0) {
                    log.warn("dubbo service ({}) is destroyed?", referenceConfig.getInterface());
                    return;
                }
            } while (!UPDATER.compareAndSet(this, c, c - 1));
            if (c == 1) {
                Service rf = dubboClient.map.remove(referenceConfig.getInterface());
                if (rf == this) {
                    log.info("destroy dubbo service({})", referenceConfig.getInterface());
                    referenceConfig.destroy();
                }
            }
        }
    }
}
