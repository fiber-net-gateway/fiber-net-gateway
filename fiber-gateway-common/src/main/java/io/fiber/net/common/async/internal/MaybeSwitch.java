package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;

public abstract class MaybeSwitch<T> extends MaybeSubject<T> implements Maybe.Observer<T> {
    private final Scheduler scheduler;

    public MaybeSwitch(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void onSubscribe(Disposable d) {
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }
}