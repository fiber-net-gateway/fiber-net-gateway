package io.fiber.net.script.parse.ir;

abstract class Exp extends Instrument {
    private ResDist dist;

    ResDist getDist() {
        return dist;
    }

    Exp setDist(ResDist dist) {
        this.dist = dist;
        return this;
    }


}
