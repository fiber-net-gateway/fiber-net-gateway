package io.fiber.net.script.aot;

import org.junit.Assert;
import org.junit.Test;

public class SsaValueTest {

    @Test
    public void shouldReplaceRepeatedOperands() {
        Block block = new Block(0);
        SsaValue oldVal = new LoadRoot(block, 0).getResult();
        SsaValue newVal = new LoadRoot(block, 1).getResult();
        Binary binary = new Binary(block, 2, Binary.Op.PLUS, oldVal, oldVal);

        Assert.assertEquals(2, oldVal.getUsedCount());

        oldVal.replaceAllUsesWith(newVal);

        Assert.assertSame(newVal, binary.getLeft());
        Assert.assertSame(newVal, binary.getRight());
        Assert.assertEquals(0, oldVal.getUsedCount());
        Assert.assertEquals(2, newVal.getUsedCount());
    }

    @Test
    public void shouldReplacePhiCaseOperands() {
        Block block = new Block(0);
        SsaValue oldVal = new LoadRoot(block, 0).getResult();
        SsaValue newVal = new LoadRoot(block, 1).getResult();
        Phi phi = new Phi(block, 2);
        phi.addCase(block, oldVal);

        oldVal.replaceAllUsesWith(newVal);

        Assert.assertSame(newVal, phi.getCases().get(0).value);
        Assert.assertEquals(0, oldVal.getUsedCount());
        Assert.assertEquals(1, newVal.getUsedCount());
    }
}
