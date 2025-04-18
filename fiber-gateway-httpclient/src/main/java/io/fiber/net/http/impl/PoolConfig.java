package io.fiber.net.http.impl;

import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.Fiber;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.fiber.net.http.util.ChannelConfig;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.TrustManagerFactory;

public class PoolConfig {
    static final String FIBER_USER_AGENT;

    static {
        FIBER_USER_AGENT = "fiber-net/" + SystemPropertyUtil.get(Constant.APP_NAME, "fn") + "/" + Fiber.VERSION;
    }

    public static final String DEF_USER_AGENT =
            SystemPropertyUtil.get("fiber.http.client.defaultUserAgent", FIBER_USER_AGENT);

    public static final int DEF_MAX_INITIAL_LINE_LENGTH
            = SystemPropertyUtil.getInt("fiber.http.client.maxInitialLineLen", 32 << 10);
    public static final int DEF_MAX_HEADER_SIZE =
            SystemPropertyUtil.getInt("fiber.http.client.maxHeaderSize", 64 << 10);
    public static final int DEF_MAX_CHUNK_SIZE =
            SystemPropertyUtil.getInt("fiber.http.client.maxChunkSize", 512 << 10);

    public static final long DEF_MAX_BODY_SIZE =
            SystemPropertyUtil.getLong("fiber.http.client.maxBodySize", 4 << 20);
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
    public static final int DEF_DNS_QUERY_TIMEOUT =
            SystemPropertyUtil.getInt("fiber.http.client.dnsQueryTimeout", 5000);
    public static final boolean DEF_USE_CROSS_CONNECTION =
            SystemPropertyUtil.getBoolean("fiber.http.client.useCrossConnection", true);

    public static final int DEF_MAX_IDLE_PER_HOST =
            SystemPropertyUtil.getInt("fiber.http.client.maxIdlePerHost", 100);
    public static final int DEF_EVICT_INTERVAL =
            SystemPropertyUtil.getInt("fiber.http.client.evictInterval", 3000);
    public static final int DEF_IDLE_TIME =
            SystemPropertyUtil.getInt("fiber.http.client.idleLiveTime", 25000);
    public static final int DEF_MAX_REQUEST_PER_CONN =
            SystemPropertyUtil.getInt("fiber.http.client.maxRequestPerConn", 800);
    public static final int DEF_CONNECT_TIMEOUT =
            SystemPropertyUtil.getInt("fiber.http.client.connectTimeout", 3000);
    public static final int DEF_REQUEST_TIMEOUT =
            SystemPropertyUtil.getInt("fiber.http.client.requestTimeout", 5000);
    public static final int DEF_UPGRADE_CONN_TIMEOUT =
            SystemPropertyUtil.getInt("fiber.http.client.upgradeConnTimeout", 70000);

    int maxIdlePerHost = DEF_MAX_IDLE_PER_HOST;
    long evictInterval = DEF_EVICT_INTERVAL;
    long idleLiveTime = DEF_IDLE_TIME;
    long upgradeConnTimeout = DEF_UPGRADE_CONN_TIMEOUT;
    ChannelConfig channelConfig = new ChannelConfig(DEF_TCP_NO_DELAY,
            DEF_TCP_KEEP_ALIVE,
            DEF_REUSE_ADDR,
            DEF_DNS_CACHE,
            DEF_DNS_CNAME_CACHE,
            DEF_DNS_QUERY_TIMEOUT);
    boolean useCrossConnection = DEF_USE_CROSS_CONNECTION;
    int maxInlineLen = DEF_MAX_INITIAL_LINE_LENGTH;
    int maxChunkLen = DEF_MAX_CHUNK_SIZE;
    int maxHeaderLen = DEF_MAX_HEADER_SIZE;
    int maxRequestPerConn = DEF_MAX_REQUEST_PER_CONN;
    TrustManagerFactory trustManager = InsecureTrustManagerFactory.INSTANCE;
    String userAgent = DEF_USER_AGENT;


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

    public boolean isUseCrossConnection() {
        return useCrossConnection;
    }

    public void setUseCrossConnection(boolean useCrossConnection) {
        this.useCrossConnection = useCrossConnection;
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

    public int getMaxRequestPerConn() {
        return maxRequestPerConn;
    }

    public void setMaxRequestPerConn(int maxRequestPerConn) {
        this.maxRequestPerConn = maxRequestPerConn;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public long getUpgradeConnTimeout() {
        return upgradeConnTimeout;
    }

    public void setUpgradeConnTimeout(long upgradeConnTimeout) {
        this.upgradeConnTimeout = upgradeConnTimeout;
    }

    public ChannelConfig getChannelConfig() {
        return channelConfig;
    }

    public void setChannelConfig(ChannelConfig channelConfig) {
        this.channelConfig = channelConfig;
    }
}
