package io.fiber.net.dubbo.nacos;

import com.alibaba.fastjson2.JSONFactory;
import io.fiber.net.common.utils.RefResourcePool;
import io.fiber.net.common.utils.StringUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DubboClient extends RefResourcePool<DubboClient.Service> {
    static {
        JsonNodeForFastJson2.config(JSONFactory.getDefaultObjectWriterProvider());
    }

    private static final Logger log = LoggerFactory.getLogger(DubboClient.class);
    private final ApplicationModel model;

    public DubboClient(DubboConfig dubboConfig) {
        super("dubboClient");
        System.setProperty("dubbo.application.logger", "slf4j");
        ApplicationConfig config = new ApplicationConfig(dubboConfig.getApplicationName());
        config.setQosCheck(false);
        config.setQosEnable(false);
        config.setMetadataType(CommonConstants.REMOTE_METADATA_STORAGE_TYPE);
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

    @Override
    protected Service doCreateRef(String key) {
        return Service.create(key, this);
    }

    public static class Service extends RefResourcePool.Ref {
        final GenericService genericService;
        private final ReferenceConfig<Object> referenceConfig;

        private Service(GenericService genericService, DubboClient dubboClient,
                        ReferenceConfig<Object> referenceConfig) {
            super(dubboClient);
            this.genericService = genericService;
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

        @Override
        protected void doClose() {
            try {
                referenceConfig.destroy();
            } catch (RuntimeException e) {
                log.warn("closing dubbo service failed, {}", refKey(), e);
            }
        }
    }
}
