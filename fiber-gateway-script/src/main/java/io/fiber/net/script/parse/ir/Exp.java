package io.fiber.net.script.parse.ir;

abstract class Exp extends Instrument {
    private ResDist dist = ResDist.NATURE;
    private UseKind useKind = UseKind.STACK_VALUE;
    private StorageKind storageKind = StorageKind.VM_STACK_FIELD;
    private Instrument emitOwner;
    private boolean inlineEmitted;

    ResDist getDist() {
        return dist;
    }

    Exp setDist(ResDist dist) {
        this.dist = dist;
        return this;
    }

    UseKind getUseKind() {
        return useKind;
    }

    void setUseKind(UseKind useKind) {
        this.useKind = useKind;
    }

    StorageKind getStorageKind() {
        return storageKind;
    }

    void setStorageKind(StorageKind storageKind) {
        this.storageKind = storageKind;
    }

    Instrument getEmitOwner() {
        return emitOwner;
    }

    void setEmitOwner(Instrument emitOwner) {
        this.emitOwner = emitOwner;
    }

    boolean isInlineEmitted() {
        return inlineEmitted;
    }

    void setInlineEmitted(boolean inlineEmitted) {
        this.inlineEmitted = inlineEmitted;
    }
}

enum UseKind {
    STACK_VALUE,
    CALL_ARG,
    CALL_SPREAD_ARG,
    POP_VALUE,
    RETURN_VALUE,
    STORE_VALUE,
    CONDITION_VALUE,
    THROW_VALUE,
    OBJECT_ARRAY_ITEM
}

enum StorageKind {
    JVM_STACK,
    JVM_LOCAL,
    VM_STACK_FIELD
}
