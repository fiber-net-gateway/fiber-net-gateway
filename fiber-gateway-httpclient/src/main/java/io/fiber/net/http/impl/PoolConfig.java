package io.fiber.net.http.impl;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.TrustManagerFactory;

public class PoolConfig {
    int maxIdlePerHost = 100;
    long evictInterval = 3000;
    long idleLiveTime = 25000;
    boolean enableDnsCache = true;
    boolean enableDnsCnameCache = true;
    boolean tcpNoDelay = true;
    boolean tcpKeepalive = true;
    boolean reuseAddr;
    int maxInlineLen = 64 << 10;
    int maxChunkLen = 8 << 10;
    int maxHeaderLen = 128 << 10;
    TrustManagerFactory trustManager = InsecureTrustManagerFactory.INSTANCE;


    public int getMaxIdlePerHost() {
        return maxIdlePerHost;
    }

    public void setMaxIdlePerHost(int maxIdlePerHost) {
        this.maxIdlePerHost = maxIdlePerHost;
    }

    public long getEvictInterval() {
        return evictInterval;
    }

    public void setEvictInterval(long evictInterval) {
        this.evictInterval = evictInterval;
    }

    public long getIdleLiveTime() {
        return idleLiveTime;
    }

    public void setIdleLiveTime(long idleLiveTime) {
        this.idleLiveTime = idleLiveTime;
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

    public int getMaxInlineLen() {
        return maxInlineLen;
    }

    public void setMaxInlineLen(int maxInlineLen) {
        this.maxInlineLen = maxInlineLen;
    }

    public int getMaxChunkLen() {
        return maxChunkLen;
    }

    public void setMaxChunkLen(int maxChunkLen) {
        this.maxChunkLen = maxChunkLen;
    }

    public int getMaxHeaderLen() {
        return maxHeaderLen;
    }

    public void setMaxHeaderLen(int maxHeaderLen) {
        this.maxHeaderLen = maxHeaderLen;
    }

    public TrustManagerFactory getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(TrustManagerFactory trustManager) {
        this.trustManager = trustManager;
    }

    public boolean isReuseAddr() {
        return reuseAddr;
    }

    public void setReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
    }
}
