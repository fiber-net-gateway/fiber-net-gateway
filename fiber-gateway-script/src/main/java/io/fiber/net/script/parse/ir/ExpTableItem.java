package io.fiber.net.script.parse.ir;

import org.objectweb.asm.Label;

class ExpTableItem {
    private final CodeEnterPoint catchPoint;
    private final Label tryStartLabel;
    private Label tryEndLabel;

    ExpTableItem(CodeEnterPoint catchPoint, Label tryStartLabel) {
        this.catchPoint = catchPoint;
        this.tryStartLabel = tryStartLabel;
    }

    void setTryEndLabel(Label tryEndLabel) {
        this.tryEndLabel = tryEndLabel;
    }

    Label getTryStartLabel() {
        return tryStartLabel;
    }

    Label getTryEndLabel() {
        return tryEndLabel;
    }

    Label getCatchLabel() {
        return catchPoint.getStartLabel();
    }
}