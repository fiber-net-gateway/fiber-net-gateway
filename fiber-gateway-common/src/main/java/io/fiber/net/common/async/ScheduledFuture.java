package io.fiber.net.common.async;

public interface ScheduledFuture {
    boolean cancel(boolean mayInterruptIfRunning);

    boolean isCancelled();

    boolean isDone();

    boolean isSuccess();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     */
    boolean isCancellable();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     * {@code null} if succeeded or this future is not
     * completed yet.
     */
    Throwable cause();
}
