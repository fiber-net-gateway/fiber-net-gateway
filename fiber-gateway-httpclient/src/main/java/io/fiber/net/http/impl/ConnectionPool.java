package io.fiber.net.http.impl;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.HttpHost;
import io.fiber.net.http.util.ConnectionFactory;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool {

    private static final FastThreadLocal<ThreadConnHolder> TH = new FastThreadLocal<>();
    private final ConnectionFactory connectionFactory;
    private final EventLoopGroup group;
    private final CompletableFuture<Void> startPromise;
    private final PoolConfig config;

    public ConnectionPool(ConnectionFactory connectionFactory, PoolConfig config) {
        this.connectionFactory = connectionFactory;
        this.group = connectionFactory.getGroup();
        this.config = config;
        startPromise = new CompletableFuture<>();
        init(group);
    }

    private void init(EventLoopGroup group) {
        ArrayList<ThreadConnHolder> holders = new ArrayList<>();
        for (EventExecutor eventExecutor : group) {
            holders.add(new ThreadConnHolder(config.getMaxIdlePerHost(), config.getEvictInterval(),
                    config.getIdleLiveTime(), (EventLoop) eventExecutor));
        }
        assert !holders.isEmpty();
        ThreadConnHolder prevHolder = null;
        for (ThreadConnHolder holder : holders) {
            if (prevHolder != null) {
                prevHolder.next = holder;
            }
            prevHolder = holder;
        }
        prevHolder.next = holders.get(0);

        AtomicInteger dec = new AtomicInteger(holders.size());
        for (ThreadConnHolder holder : holders) {
            holder.executor.execute(() -> {
                if (TH.isSet() && TH.getIfExists() != holder) {
                    startPromise.completeExceptionally(new IllegalStateException("EventLoopGroup was bound ConnectionPool??"));
                    return;
                }
                TH.set(holder);
                if (dec.decrementAndGet() == 0) {
                    startPromise.complete(null);
                }
            });
        }
    }

    public CompletableFuture<Void> getStartPromise() {
        return startPromise;
    }

    public boolean isStart() {
        return startPromise.isDone() && !startPromise.isCompletedExceptionally();
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public HttpConnection getExistsConn(HttpHost httpHost) {
        assert Scheduler.isInIOThread(group) : "not in io thread??";
        final ThreadConnHolder holder = TH.get();
        return holder.tryGet(httpHost);
    }

    public interface ConnFetcher {
        void onConnSuccess(HttpConnection connection);

        void onConnError(Throwable err);

        default int connectTimeout() {
            return 0;
        }

        HttpHost httpHost();
    }

    public void getConn(ConnFetcher fetcher) {
        assert Scheduler.isInIOThread(group) : "not in io thread??";
        final ThreadConnHolder holder = TH.get();
        HttpConnection httpConnection = holder.tryGet(fetcher.httpHost());
        if (httpConnection != null) {
            fetcher.onConnSuccess(httpConnection);
            return;
        }

        PoolConfig config = this.config;
        SimpleFetcher cycleConnFetcher = new SimpleFetcher(holder, connectionFactory, fetcher, config);
        if (config.useCrossConnection && cycleConnFetcher.deferTryFetch(holder)) {
            return;
        }
        cycleConnFetcher.doConnect();
    }

    private static class SimpleFetcher implements Runnable, ConnectionFactory.ConnCallback {
        private final ThreadConnHolder originalHolder;
        private final ConnectionFactory ConnectionFactory;
        private final ConnFetcher connFetcher;
        private final PoolConfig poolConfig;

        private ThreadConnHolder holder;
        private HttpConnectionHandler handler;

        private SimpleFetcher(ThreadConnHolder originalHolder,
                              ConnectionFactory ConnectionFactory,
                              ConnFetcher connFetcher,
                              PoolConfig poolConfig) {
            this.originalHolder = originalHolder;
            this.ConnectionFactory = ConnectionFactory;
            this.connFetcher = connFetcher;
            this.poolConfig = poolConfig;
        }

        public boolean deferTryFetch(ThreadConnHolder holder) {
            ThreadConnHolder n;
            HttpHost httpHost = connFetcher.httpHost();

            while ((n = holder.next) != originalHolder) {
                if (n.isAvailable(httpHost)) {
                    this.holder = n;
                    n.executor.execute(this);
                    return true;
                }
                holder = n;
            }
            return false;
        }

        @Override
        public void run() {
            ThreadConnHolder holder = this.holder;
            HttpConnection httpConnection = holder.tryGet(connFetcher.httpHost());
            if (httpConnection != null) {
                connFetcher.onConnSuccess(httpConnection);
                return;
            }

            if (holder != originalHolder) {
                // 没检查完一圈
                if (deferTryFetch(holder)) {
                    return;
                }
                // 提交给原始线程
                this.holder = originalHolder;
                this.holder.executor.execute(this);
                return;
            }
            doConnect();
        }

        public void doConnect() {
            this.holder = originalHolder;
            // 原始线程直接连接
            EventLoop executor = originalHolder.executor;
            ConnectionFactory.connect(connFetcher.httpHost().getAddress(),
                    connFetcher.connectTimeout(),
                    executor,
                    this);
        }

        @Override
        public void onChannelCreated(Channel channel, EventLoop loop) {
            channel.pipeline().addLast(handler = new HttpConnectionHandler(channel,
                    connFetcher.httpHost(),
                    loop,
                    poolConfig));
        }

        @Override
        public void onConnectSuccess(Channel channel) {
            HttpConnectionHandler handler = this.handler;
            assert handler.getCh() == channel
                    && channel.eventLoop() == originalHolder.executor
                    && originalHolder.executor.inEventLoop();
            // new connection
            originalHolder.markConnCreate(handler);
            connFetcher.onConnSuccess(handler);
        }

        @Override
        public void onConnectError(Throwable e) {
            connFetcher.onConnError(e);
        }
    }

}
