package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Dominators {
    final Map<Block, Block> idom;
    final Map<Block, List<Block>> children;
    final List<Block> reversePostOrder;
    final Map<Block, Integer> order;

    private Dominators(Map<Block, Block> idom,
                       Map<Block, List<Block>> children,
                       List<Block> reversePostOrder,
                       Map<Block, Integer> order) {
        this.idom = idom;
        this.children = children;
        this.reversePostOrder = reversePostOrder;
        this.order = order;
    }

    static Dominators compute(Cfg cfg) {
        List<Block> rpo = reversePostOrder(cfg.getEntryBlock());
        Map<Block, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < rpo.size(); i++) {
            order.put(rpo.get(i), i);
        }

        Map<Block, Block> idom = new IdentityHashMap<>();
        Block entry = cfg.getEntryBlock();
        idom.put(entry, entry);
        boolean changed;
        do {
            changed = false;
            for (int i = 1; i < rpo.size(); i++) {
                Block block = rpo.get(i);
                Block newIdom = null;
                for (Edge edge : block.getPredecessors()) {
                    Block predecessor = edge.predecessor;
                    if (!idom.containsKey(predecessor)) {
                        continue;
                    }
                    newIdom = newIdom == null ? predecessor : intersect(predecessor, newIdom, idom, order);
                }
                if (newIdom != null && idom.get(block) != newIdom) {
                    idom.put(block, newIdom);
                    changed = true;
                }
            }
        } while (changed);

        Map<Block, List<Block>> children = new IdentityHashMap<>();
        for (Block block : rpo) {
            Block parent = idom.get(block);
            if (parent == null || parent == block) {
                continue;
            }
            List<Block> list = children.get(parent);
            if (list == null) {
                list = new ArrayList<>();
                children.put(parent, list);
            }
            list.add(block);
        }
        return new Dominators(idom, children, rpo, order);
    }

    boolean dominates(Block dominator, Block block) {
        if (dominator == block) {
            return true;
        }
        if (!idom.containsKey(dominator) || !idom.containsKey(block)) {
            return false;
        }
        Block current = block;
        while (true) {
            Block parent = idom.get(current);
            if (parent == null || parent == current) {
                return false;
            }
            if (parent == dominator) {
                return true;
            }
            current = parent;
        }
    }

    private static Block intersect(Block left, Block right,
                                   Map<Block, Block> idom,
                                   Map<Block, Integer> order) {
        while (left != right) {
            while (order.get(left) > order.get(right)) {
                left = idom.get(left);
            }
            while (order.get(right) > order.get(left)) {
                right = idom.get(right);
            }
        }
        return left;
    }

    private static List<Block> reversePostOrder(Block entry) {
        Set<Block> visited = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        List<Block> postorder = new ArrayList<>();
        dfs(entry, visited, postorder);
        Collections.reverse(postorder);
        return postorder;
    }

    private static void dfs(Block block, Set<Block> visited, List<Block> postorder) {
        if (!visited.add(block)) {
            return;
        }
        for (Edge edge : block.getSuccessors()) {
            dfs(edge.successor, visited, postorder);
        }
        postorder.add(block);
    }
}
