package io.fiber.net.http.util;

public class ChannelConfig {
    boolean tcpNoDelay = true;
    boolean tcpKeepalive = true;
    boolean reuseAddr;
    boolean enableDnsCache = true;
    boolean enableDnsCnameCache = true;
    int dnsQueryTimeout = 5000;

    public ChannelConfig() {
    }

    public ChannelConfig(boolean tcpNoDelay,
                         boolean tcpKeepalive,
                         boolean reuseAddr,
                         boolean enableDnsCache,
                         boolean enableDnsCnameCache,
                         int dnsQueryTimeout) {
        this.tcpNoDelay = tcpNoDelay;
        this.tcpKeepalive = tcpKeepalive;
        this.reuseAddr = reuseAddr;
        this.enableDnsCache = enableDnsCache;
        this.enableDnsCnameCache = enableDnsCnameCache;
        this.dnsQueryTimeout = dnsQueryTimeout;
    }

    public boolean isEnableDnsCache() {
        return enableDnsCache;
    }

    public void setEnableDnsCache(boolean enableDnsCache) {
        this.enableDnsCache = enableDnsCache;
    }

    public boolean isEnableDnsCnameCache() {
        return enableDnsCnameCache;
    }

    public void setEnableDnsCnameCache(boolean enableDnsCnameCache) {
        this.enableDnsCnameCache = enableDnsCnameCache;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isTcpKeepalive() {
        return tcpKeepalive;
    }

    public void setTcpKeepalive(boolean tcpKeepalive) {
        this.tcpKeepalive = tcpKeepalive;
    }

    public boolean isReuseAddr() {
        return reuseAddr;
    }

    public void setReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
    }
}
