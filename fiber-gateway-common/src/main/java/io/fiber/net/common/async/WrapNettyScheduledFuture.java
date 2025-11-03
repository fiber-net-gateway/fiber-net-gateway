package io.fiber.net.common.async;

class WrapNettyScheduledFuture implements ScheduledFuture {
    private final io.netty.util.concurrent.ScheduledFuture<?> future;

    WrapNettyScheduledFuture(io.netty.util.concurrent.ScheduledFuture<?> future) {
        this.future = future;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public boolean isSuccess() {
        return future.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return future.isCancellable();
    }

    @Override
    public Throwable cause() {
        return future.cause();
    }
}
