package io.fiber.net.common.utils;

import io.fiber.net.common.FiberException;

public final class Exceptions {
    public static final FiberException OB_CONSUMED = new FiberException("value consumed", 500, "OB_CONSUMED");


    private Exceptions() {
    }

    public static RuntimeException wrapOrThrow(Throwable error) {
        if (error instanceof Error) {
            throw (Error) error;
        }
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        return new RuntimeException(error);
    }

    public static void throwIfFatal(Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        } else if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        } else if (t instanceof LinkageError) {
            throw (LinkageError) t;
        }
    }
}