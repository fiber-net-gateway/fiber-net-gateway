package io.fiber.net.script.aot;

import io.fiber.net.common.json.IntNode;
import org.junit.Assert;
import org.junit.Test;

public class SsaDestructionTest {

    @Test
    public void shouldReturnEmptyResultWhenNoPhiExists() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.setEntryBlock(cfg.mustGetBlock(0));

        SsaDestruction.Result result = new SsaDestruction(cfg).analyze();

        Assert.assertTrue(result.getEdgeCopies().isEmpty());
        Assert.assertFalse(result.hasPhi(cfg.mustGetBlock(0)));
    }

    @Test
    public void shouldLowerPhiCasesToEdgeCopies() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        cfg.addBlock(2);
        Block left = cfg.mustGetBlock(0);
        Block right = cfg.mustGetBlock(1);
        Block merge = cfg.mustGetBlock(2);
        cfg.setEntryBlock(left);

        LoadConst one = new LoadConst(left, 0, IntNode.valueOf(1));
        LoadConst two = new LoadConst(right, 1, IntNode.valueOf(2));
        left.getInstructions().add(one);
        right.getInstructions().add(two);

        Phi phi = merge.newPhi();
        phi.addCase(left, one.getResult());
        phi.addCase(right, two.getResult());
        merge.addPhi(phi);
        cfg.addEdge(Edge.Type.FALLTHROUGH, left, merge);
        cfg.addEdge(Edge.Type.JUMP, right, merge);

        SsaDestruction.Result result = new SsaDestruction(cfg).analyze();
        Edge leftEdge = left.getSuccessors().get(0);
        Edge rightEdge = right.getSuccessors().get(0);

        Assert.assertEquals(2, result.getEdgeCopies().size());
        assertSingleMove(result.getEdgeCopy(leftEdge), phi.getResult(), one.getResult());
        assertSingleMove(result.getEdgeCopy(rightEdge), phi.getResult(), two.getResult());
        Assert.assertTrue(result.hasPhi(merge));
    }

    @Test
    public void shouldSkipSelfMoves() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        Block predecessor = cfg.mustGetBlock(0);
        Block merge = cfg.mustGetBlock(1);
        cfg.setEntryBlock(predecessor);

        LoadConst one = new LoadConst(predecessor, 0, IntNode.valueOf(1));
        predecessor.getInstructions().add(one);
        Phi phi = merge.newPhi();
        phi.addCase(predecessor, one.getResult());
        phi.setResult(one.getResult());
        merge.addPhi(phi);
        cfg.addEdge(Edge.Type.FALLTHROUGH, predecessor, merge);

        SsaDestruction.Result result = new SsaDestruction(cfg).analyze();

        Assert.assertTrue(result.getEdgeCopies().isEmpty());
        Assert.assertTrue(result.hasPhi(merge));
    }

    @Test
    public void shouldMarkConditionalEdgeCopiesAsVirtual() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        cfg.addBlock(2);
        Block predecessor = cfg.mustGetBlock(0);
        Block other = cfg.mustGetBlock(1);
        Block merge = cfg.mustGetBlock(2);
        cfg.setEntryBlock(predecessor);

        LoadConst one = new LoadConst(predecessor, 0, IntNode.valueOf(1));
        LoadConst two = new LoadConst(other, 1, IntNode.valueOf(2));
        predecessor.getInstructions().add(one);
        other.getInstructions().add(two);

        Phi phi = merge.newPhi();
        phi.addCase(predecessor, one.getResult());
        phi.addCase(other, two.getResult());
        merge.addPhi(phi);
        cfg.addEdge(Edge.Type.FALLTHROUGH, predecessor, merge);
        cfg.addEdge(Edge.Type.JUMP, predecessor, other);
        cfg.addEdge(Edge.Type.JUMP, other, merge);

        SsaDestruction.Result result = new SsaDestruction(cfg).analyze();
        Edge conditionalEdge = findEdge(predecessor, merge);
        Edge directEdge = findEdge(other, merge);

        Assert.assertTrue(result.getEdgeCopy(conditionalEdge).needsVirtualBlock());
        Assert.assertFalse(result.getEdgeCopy(directEdge).needsVirtualBlock());
    }

    @Test
    public void shouldMarkThrowEdgeCopiesAsVirtual() {
        Cfg cfg = new Cfg();
        cfg.addBlock(0);
        cfg.addBlock(1);
        Block throwing = cfg.mustGetBlock(0);
        Block handler = cfg.mustGetBlock(1);
        cfg.setEntryBlock(throwing);

        LoadConst one = new LoadConst(throwing, 0, IntNode.valueOf(1));
        throwing.getInstructions().add(one);
        Phi phi = handler.newPhi();
        phi.addCase(throwing, one.getResult());
        handler.addPhi(phi);
        cfg.addEdge(Edge.Type.THROW, throwing, handler);

        SsaDestruction.Result result = new SsaDestruction(cfg).analyze();

        Assert.assertTrue(result.getEdgeCopy(throwing.getSuccessors().get(0)).needsVirtualBlock());
    }

    private static void assertSingleMove(SsaDestruction.EdgeCopy edgeCopy, SsaValue dst, SsaValue src) {
        Assert.assertNotNull(edgeCopy);
        Assert.assertEquals(1, edgeCopy.getMoves().size());
        Assert.assertSame(dst, edgeCopy.getMoves().get(0).getDst());
        Assert.assertSame(src, edgeCopy.getMoves().get(0).getSrc());
    }

    private static Edge findEdge(Block predecessor, Block successor) {
        for (Edge edge : predecessor.getSuccessors()) {
            if (edge.successor == successor) {
                return edge;
            }
        }
        throw new AssertionError("missing edge");
    }
}
