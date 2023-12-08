package io.fiber.net.http.impl;

import io.fiber.net.http.HttpHost;
import io.netty.channel.EventLoop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class ThreadConnHolder {

    static class ConnList implements Runnable {
        private static final AtomicIntegerFieldUpdater<ConnList> IDLE_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(ConnList.class, "idleCount");
        private static final AtomicIntegerFieldUpdater<ConnList> TOTAL_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(ConnList.class, "totalCount");

        final HttpHost httpHost;
        final ThreadConnHolder holder;
        private boolean evictTimerStart;

        HttpConnection head;
        HttpConnection tail;


        private volatile int idleCount;
        private volatile int totalCount;

        private ConnList(HttpHost httpHost, ThreadConnHolder holder) {
            this.httpHost = httpHost;
            this.holder = holder;
        }

        boolean putHead(HttpConnection conn) {
            int maxIdle = holder.maxIdle;
            for (; ; ) {
                int c = idleCount;
                if (c > maxIdle) {
                    return false;
                }
                if (IDLE_UPDATER.compareAndSet(this, c, c + 1)) {
                    break;
                }
            }

            HttpConnection h;
            if ((h = head) != null) {
                conn.next = h;
                h.prev = conn;
                this.head = conn;
            } else {
                tail = this.head = conn;
            }

            startEvictTimer();
            return true;
        }

        HttpConnection fetchHead() {
            IDLE_UPDATER.decrementAndGet(this);
            HttpConnection head;
            if ((head = this.head) == null) {
                return null;
            }
            if ((this.head = head.next) == null) {
                this.tail = null;
            } else {
                this.head.prev = null;
                head.next = null;
            }

            return head;
        }

        HttpConnection fetchTail() {
            IDLE_UPDATER.decrementAndGet(this);
            HttpConnection tail;
            if ((tail = this.tail) == null) {
                return null;
            }

            if ((this.tail = tail.prev) == null) {
                this.head = null;
            } else {
                this.tail.next = null;
                tail.prev = null;
            }
            return tail;
        }

        void incrementTotal() {
            TOTAL_UPDATER.incrementAndGet(this);
        }

        void decrementTotal() {
            TOTAL_UPDATER.decrementAndGet(this);
        }

        void removeConn(HttpConnection conn) {
            assert this == conn.connList;
            IDLE_UPDATER.decrementAndGet(this);
            HttpConnection p = conn.prev, n = conn.next;
            if (p == null) {
                assert conn == head : "ConnList not contains conn";
                head = n;
            } else {
                p.next = n;
                conn.prev = null;
            }

            if (n == null) {
                assert conn == tail : "ConnList not contains conn";
                tail = p;
            } else {
                n.prev = p;
                conn.next = null;
            }
        }

        boolean isEmpty() {
            return head == null;
        }

        @Override
        public void run() {
            this.evictTimerStart = false;
            long l = System.currentTimeMillis();
            for (; ; ) {
                HttpConnection httpConnection = tail;
                if (httpConnection == null) {
                    break;
                }
                if (l - httpConnection.getLastUpdateTime() >= holder.idleLiveTime || !httpConnection.isActive()) {
                    httpConnection.close();
                } else {
                    break;
                }
            }
            startEvictTimer();
        }

        private void startEvictTimer() {
            if (idleCount > 0 && !this.evictTimerStart) {
                this.evictTimerStart = true;
                holder.executor.schedule(this, holder.evictInterval, TimeUnit.MILLISECONDS);
            } else {
                if (totalCount == 0) {
                    holder.map.remove(httpHost);
                }
            }
        }
    }

    private final Map<HttpHost, ConnList> map = new ConcurrentHashMap<>();
    private final int maxIdle;
    private final long evictInterval;
    private final long idleLiveTime;

    final EventLoop executor;
    ThreadConnHolder next;

    public ThreadConnHolder(int maxIdle, long evictInterval, long idleLiveTime, EventLoop executor) {
        this.maxIdle = maxIdle;
        this.evictInterval = evictInterval;
        this.idleLiveTime = idleLiveTime;
        this.executor = executor;
    }

    HttpConnection tryGet(HttpHost httpHost) {
        assert executor.inEventLoop();
        ConnList list = map.get(httpHost);
        if (list == null || list.isEmpty()) {
            return null;
        }

        HttpConnection httpConnection = list.fetchHead();
        httpConnection.detached(list);
        return httpConnection;
    }

    boolean isAvailable(HttpHost httpHost) {
        ConnList list = map.get(httpHost);
        return list != null && list.idleCount > 0;
    }

    void markConnCreate(HttpConnection connection) {
        ConnList list = map.computeIfAbsent(connection.getHttpHost(), k -> {
            ConnList connList = new ConnList(k, this);
            connList.totalCount = 1;
            connection.connList = connList;
            return connList;
        });
        if (connection.connList == null) {
            connection.connList = list;
            list.incrementTotal();
        }
    }

}
