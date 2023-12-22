package io.fiber.net.dubbo.nacos;

public class DubboConfig {
    private String applicationName = "fiber-net-gateway";
    private String registryAddr;
    private String protocol = "dubbo";

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getRegistryAddr() {
        return registryAddr;
    }

    public void setRegistryAddr(String registryAddr) {
        this.registryAddr = registryAddr;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return "DubboConfig{" +
                "applicationName='" + applicationName + '\'' +
                ", registryAddr='" + registryAddr + '\'' +
                ", protocol='" + protocol + '\'' +
                '}';
    }
}
