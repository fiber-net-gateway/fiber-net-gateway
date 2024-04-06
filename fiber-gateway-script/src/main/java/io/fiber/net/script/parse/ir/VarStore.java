package io.fiber.net.script.parse.ir;

abstract class VarStore extends Instrument {
    private final int storeIdx;

    int getStoreIdx() {
        return storeIdx;
    }

    VarStore(int storeIdx) {
        this.storeIdx = storeIdx;
    }

}
