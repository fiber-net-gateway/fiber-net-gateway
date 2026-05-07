package io.fiber.net.script.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class GlobalValueNumberingPass implements SsaPass {
    @Override
    public boolean run(SsaGraph graph) {
        Numbering numbering = new Numbering();
        boolean changed = visit(graph, graph.entryBlock(), new Scope(), numbering);
        if (changed) {
            new DeadCodeEliminationPass().run(graph);
        }
        return changed;
    }

    private boolean visit(SsaGraph graph, SsaBlock block, Scope inherited, Numbering numbering) {
        boolean changed = false;
        Scope scope = new Scope(inherited);
        for (SsaValue phi : block.phis()) {
            numbering.numberOf(phi);
        }
        for (SsaValue value : new ArrayList<>(block.instructions())) {
            numbering.numberOf(value);
            ValueKey key = numbering.keyOf(value);
            if (key != null && CommonSubexpressionEliminationPass.isCandidate(value)) {
                SsaValue leader = scope.leader(key);
                if (leader == null) {
                    scope.putLeader(key, value);
                } else if (leader != value && graph.dominatorTree().dominates(leader.basicBlock(), value.basicBlock())) {
                    changed |= SsaEditor.replaceAllUses(graph, value, leader);
                }
            }
        }
        for (BasicBlock child : graph.dominatorTree().children(block.basicBlock())) {
            SsaBlock childBlock = graph.blockFor(child);
            if (childBlock != null) {
                changed |= visit(graph, childBlock, scope, numbering);
            }
        }
        return changed;
    }

    private static final class Scope {
        private final HashMap<ValueKey, SsaValue> leaders;

        private Scope() {
            this.leaders = new HashMap<>();
        }

        private Scope(Scope inherited) {
            this.leaders = new HashMap<>(inherited.leaders);
        }

        private SsaValue leader(ValueKey key) {
            return leaders.get(key);
        }

        private void putLeader(ValueKey key, SsaValue value) {
            leaders.put(key, value);
        }
    }

    private static final class Numbering {
        private int nextNumber = 1;
        private final Map<SsaValue, Integer> numbers = new IdentityHashMap<>();
        private final Set<SsaValue> visiting = Collections.newSetFromMap(new IdentityHashMap<SsaValue, Boolean>());
        private final HashMap<ValueKey, Integer> keyNumbers = new HashMap<>();
        private final Map<SsaValue, ValueKey> keys = new IdentityHashMap<>();

        private int numberOf(SsaValue value) {
            Integer number = numbers.get(value);
            if (number != null) {
                return number;
            }
            if (!visiting.add(value)) {
                return 0;
            }
            int computed = computeNumber(value);
            visiting.remove(value);
            if (computed == 0) {
                computed = freshNumber();
            }
            numbers.put(value, computed);
            return computed;
        }

        private ValueKey keyOf(SsaValue value) {
            return keys.get(value);
        }

        private int computeNumber(SsaValue value) {
            if (SsaEffects.isCopy(value)) {
                return numberOf(value.operands().get(0));
            }
            if (value.kind() == SsaKind.PHI) {
                return phiNumber(value);
            }
            if (!CommonSubexpressionEliminationPass.isCandidate(value)) {
                return freshNumber();
            }
            ValueKey key = numberedKey(value);
            keys.put(value, key);
            Integer number = keyNumbers.get(key);
            if (number == null) {
                number = freshNumber();
                keyNumbers.put(key, number);
            }
            return number;
        }

        private int phiNumber(SsaValue phi) {
            int merged = 0;
            for (SsaValue operand : phi.operands()) {
                if (operand == phi) {
                    continue;
                }
                int number = numberOf(operand);
                if (number == 0) {
                    return freshNumber();
                }
                if (merged == 0) {
                    merged = number;
                } else if (merged != number) {
                    return freshNumber();
                }
            }
            return merged;
        }

        private ValueKey numberedKey(SsaValue value) {
            int[] operands = new int[value.operands().size()];
            for (int i = 0; i < operands.length; i++) {
                operands[i] = numberOf(value.operands().get(i));
            }
            return ValueKey.forValueNumbers(value, operands);
        }

        private int freshNumber() {
            return nextNumber++;
        }
    }
}
