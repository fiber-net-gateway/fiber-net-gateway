package io.fiber.net.common.utils;

import org.junit.Test;

public class RefResourcePoolTest {

    private static class Pool extends RefResourcePool<Pool.R> {

        protected Pool() {
            super("Pool");
        }

        @Override
        protected Pool.R doCreateRef(String key) {
            System.out.println("created:" + key);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if ("aaa".equals(key)) {

                getOrCreate("bbb");
            } else if ("bbb".equals(key)) {
                getOrCreate("aaa");
            }
            return new R(this);
        }

        private static class R extends RefResourcePool.Ref {

            protected R(RefResourcePool<? extends Ref> pool) {
                super(pool);
            }

            @Override
            protected void doClose() {
                System.out.println("doClose:" + refKey());
            }
        }
    }

    Pool pool = new Pool();

    @Test
    public void getOrCreate() {
        boolean error = false;
        try {
            Pool.R r = pool.getOrCreate("aaa");
            System.out.println(r);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            error = true;
        }
        Assert.isTrue(error);
    }

    @Test
    public void getOrCreate2() {
        boolean error = false;
        try {
            Pool.R r = pool.getOrCreate("aaa");
            System.out.println(r);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            error = true;
        }
        Assert.isTrue(error);
    }

    @Test
    public void doCreateRef() throws InterruptedException {
        Thread thread = new Thread(() -> {
            Pool.R b = pool.getOrCreate("bbb");
        });
        thread.start();

        Pool.R a = pool.getOrCreate("aaa");
        thread.join();
    }
}