package io.fiber.net.server;

import io.fiber.net.common.utils.SystemPropertyUtil;

public class ServerConfig {
    public static final int DEF_BACKLOG = SystemPropertyUtil.getInt("fiber.http.server.backlog", 128);
    public static final int DEF_MAX_INITIAL_LINE_LENGTH
            = SystemPropertyUtil.getInt("fiber.http.server.maxInitialLineLen", 32 << 10);
    public static final int DEF_MAX_HEADER_SIZE =
            SystemPropertyUtil.getInt("fiber.http.server.maxHeaderSize", 64 << 10);
    public static final int DEF_MAX_CHUNK_SIZE =
            SystemPropertyUtil.getInt("fiber.http.server.maxHeaderSize", 128 << 10);
    public static final int DEF_SERVER_PORT =
            SystemPropertyUtil.getInt("fiber.http.server.serverPort", 16688);

    public static final int DEF_MAX_BODY_SIZE =
            SystemPropertyUtil.getInt("fiber.http.server.maxBodySize", 16 << 20);
    public static final boolean DEF_TCP_NO_DELAY =
            SystemPropertyUtil.getBoolean("fiber.http.server.tcpNoDelay", true);
    public static final boolean DEF_TCP_KEEP_ALIVE =
            SystemPropertyUtil.getBoolean("fiber.http.server.tcpKeepAlive", true);

    private int backlog = DEF_BACKLOG;
    private int maxInitialLineLength = DEF_MAX_INITIAL_LINE_LENGTH;
    private int maxHeaderSize = DEF_MAX_HEADER_SIZE;
    private int maxChunkSize = DEF_MAX_CHUNK_SIZE;
    private int serverPort = DEF_SERVER_PORT;

    private int maxBodySize = DEF_MAX_BODY_SIZE;
    private boolean tcpNoDelay = DEF_TCP_NO_DELAY;
    private boolean tcpKeepAlive = DEF_TCP_KEEP_ALIVE;
    private String bindIp;

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isTcpKeepAlive() {
        return tcpKeepAlive;
    }

    public void setTcpKeepAlive(boolean tcpKeepAlive) {
        this.tcpKeepAlive = tcpKeepAlive;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public String getBindIp() {
        return bindIp;
    }

    public void setBindIp(String bindIp) {
        this.bindIp = bindIp;
    }
}
