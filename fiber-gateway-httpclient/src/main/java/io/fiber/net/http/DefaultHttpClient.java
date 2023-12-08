package io.fiber.net.http;

import io.fiber.net.http.impl.ConnectionPool;
import io.fiber.net.http.impl.PoolConfig;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.CompletableFuture;

public class DefaultHttpClient implements HttpClient {
    private final ConnectionPool connectionPool;

    public DefaultHttpClient(EventLoopGroup group) {
        this(group, new PoolConfig());
    }

    public DefaultHttpClient(EventLoopGroup group, PoolConfig pc) {
        this.connectionPool = new ConnectionPool(group, pc);
    }

    public CompletableFuture<Void> getStartPromise() {
        return connectionPool.getStartPromise();
    }

    @Override
    public ClientExchange refer(String host, int port) {
        return refer(new SingleHostFetcher(host, port));
    }

    @Override
    public ClientExchange refer(HttpHost httpHost) {
        return refer(new SingleHostFetcher(httpHost));
    }

    @Override
    public ClientExchange refer(HostFetcher hostFetcher) {
        return new ClientExchange(connectionPool, hostFetcher);
    }


}
