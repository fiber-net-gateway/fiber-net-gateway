package io.fiber.net.http;

import io.fiber.net.common.ioc.Initializable;
import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.PoolConfig;
import io.fiber.net.http.util.ConnectionFactory;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.CompletableFuture;

public class DefaultHttpClient implements HttpClient, Initializable {
    private final ConnectionPool connectionPool;

    public DefaultHttpClient(EventLoopGroup group) {
        this(group, new PoolConfig());
    }

    public DefaultHttpClient(EventLoopGroup group, PoolConfig poolConfig) {
        this(new ConnectionFactory(group, poolConfig.getChannelConfig()), poolConfig);
    }

    public DefaultHttpClient(ConnectionFactory connectionFactory, PoolConfig pc) {
        this.connectionPool = new ConnectionPool(connectionFactory, pc);
    }

    public CompletableFuture<Void> getStartPromise() {
        return connectionPool.getStartPromise();
    }

    @Override
    public ClientExchange refer(String host, int port) {
        return refer(HttpHost.create(host, port));
    }

    @Override
    public ClientExchange refer(HttpHost httpHost) {
        return new ClientExchange(connectionPool, httpHost);
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionPool.getConnectionFactory();
    }

    @Override
    public void init() {
        try {
            connectionPool.getStartPromise().get();
        } catch (Exception e) {
            throw new IllegalStateException("init start failed", e);
        }
    }
}
