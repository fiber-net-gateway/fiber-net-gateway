package io.fiber.net.http.impl;

import io.fiber.net.common.utils.SystemPropertyUtil;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.TrustManagerFactory;

public class PoolConfig {
    public static final int DEF_MAX_INITIAL_LINE_LENGTH
            = SystemPropertyUtil.getInt("fiber.http.client.maxInitialLineLen", 32 << 10);
    public static final int DEF_MAX_HEADER_SIZE =
            SystemPropertyUtil.getInt("fiber.http.client.maxHeaderSize", 64 << 10);
    public static final int DEF_MAX_CHUNK_SIZE =
            SystemPropertyUtil.getInt("fiber.http.client.maxHeaderSize", 128 << 10);

    public static final int DEF_MAX_BODY_SIZE =
            SystemPropertyUtil.getInt("fiber.http.client.maxBodySize", 16 << 20);
    public static final boolean DEF_TCP_NO_DELAY =
            SystemPropertyUtil.getBoolean("fiber.http.client.tcpNoDelay", true);
    public static final boolean DEF_TCP_KEEP_ALIVE =
            SystemPropertyUtil.getBoolean("fiber.http.client.tcpKeepAlive", true);
    public static final boolean DEF_REUSE_ADDR =
            SystemPropertyUtil.getBoolean("fiber.http.client.reuseAddr", false);
    public static final boolean DEF_DNS_CACHE =
            SystemPropertyUtil.getBoolean("fiber.http.client.dnsCache", true);
    public static final boolean DEF_DNS_CNAME_CACHE =
            SystemPropertyUtil.getBoolean("fiber.http.client.dnsCnameCache", true);

    public static final int DEF_MAX_IDLE_PER_HOST =
            SystemPropertyUtil.getInt("fiber.http.client.maxIdlePerHost", 100);
    public static final int DEF_EVICT_INTERVAL =
            SystemPropertyUtil.getInt("fiber.http.client.evictInterval", 3000);
    public static final int DEF_IDLE_TIME =
            SystemPropertyUtil.getInt("fiber.http.client.idleLiveTime", 25000);
    public static final int DEF_MAX_REQUEST_PER_CONN =
            SystemPropertyUtil.getInt("fiber.http.client.maxRequestPerConn", 150);

    int maxIdlePerHost = DEF_MAX_IDLE_PER_HOST;
    long evictInterval = DEF_EVICT_INTERVAL;
    long idleLiveTime = DEF_IDLE_TIME;
    boolean enableDnsCache = DEF_DNS_CACHE;
    boolean enableDnsCnameCache = DEF_DNS_CNAME_CACHE;
    boolean tcpNoDelay = DEF_TCP_NO_DELAY;
    boolean tcpKeepalive = DEF_TCP_KEEP_ALIVE;
    boolean reuseAddr = DEF_REUSE_ADDR;
    int maxInlineLen = DEF_MAX_INITIAL_LINE_LENGTH;
    int maxChunkLen = DEF_MAX_CHUNK_SIZE;
    int maxHeaderLen = DEF_MAX_HEADER_SIZE;
    int maxRequestPerConn = DEF_MAX_REQUEST_PER_CONN;
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

    public int getMaxRequestPerConn() {
        return maxRequestPerConn;
    }

    public void setMaxRequestPerConn(int maxRequestPerConn) {
        this.maxRequestPerConn = maxRequestPerConn;
    }
}
