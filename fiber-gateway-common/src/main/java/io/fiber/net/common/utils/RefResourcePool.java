package io.fiber.net.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * cycle dependency may be cause deadlock
 * @param <V>
 */
public abstract class RefResourcePool<V extends RefResourcePool.Ref> {
    private static final Logger log = LoggerFactory.getLogger(RefResourcePool.class);
    private final Map<String, V> map = new ConcurrentHashMap<>();
    private final String poolName;

    protected RefResourcePool(String poolName) {
        this.poolName = poolName;
    }

    public final V getOrCreate(String key) {
        while (true) {
            V ref = map.computeIfAbsent(key, k -> {
                V v = doCreateRef(k);
                v.setKey(k);
                log.info("ref ({}) of {} is created", k, poolName);
                return v;
            });
            if (ref.addRef()) {
                return ref;
            }
            if (map.remove(key, ref)) {
                log.info("ref ({}) of {} is destroying", key, poolName);
                ref.doClose();
            }
        }
    }

    protected abstract V doCreateRef(String key);

    public abstract static class Ref {
        private static final AtomicIntegerFieldUpdater<Ref> UPDATER
                = AtomicIntegerFieldUpdater.newUpdater(Ref.class, "refCount");
        private Thread creator = Thread.currentThread();
        private volatile int refCount = 1;
        private final RefResourcePool<? extends Ref> pool;
        private String key;

        protected Ref(RefResourcePool<? extends Ref> pool) {
            this.pool = pool;
        }

        void setKey(String key) {
            this.key = key;
        }

        protected final String refKey() {
            return key;
        }

        protected abstract void doClose();

        final boolean addRef() {
            if (creator != null && Thread.currentThread() == creator) {
                creator = null;
                return true;
            }
            int c;
            do {
                c = refCount;
                if (c <= 0) {
                    log.warn("ref object ({}) of {} is destroyed?", refKey(), pool.poolName);
                    return false;
                }
            } while (!UPDATER.compareAndSet(this, c, c + 1));
            return true;
        }


        public final void destroy() {
            int c;
            do {
                c = refCount;
                if (c <= 0) {
                    log.warn("ref object ({}) of {} is destroyed?", refKey(), pool.poolName);
                    return;
                }
            } while (!UPDATER.compareAndSet(this, c, c - 1));
            if (c == 1) {
                if (pool.map.remove(refKey(), this)) {
                    log.info("ref object({}) of {} destroying", refKey(), pool.poolName);
                    doClose();
                }
            }
        }
    }
}
