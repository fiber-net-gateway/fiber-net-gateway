package io.fiber.net.script.aot;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.script.Library;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValueAllocator {

    private static final String JSON_NODE_DESC = "Lio/fiber/net/common/json/JsonNode;";
    private static final String VALUE_NODE_DESC = "Lio/fiber/net/common/json/ValueNode;";

    private final Cfg cfg;
    private final LivenessAnalysis.Result liveness;
    private final AsyncSpillAnalysis.Result asyncSpills;
    private final SsaDestruction.Result ssaDestruction;

    public ValueAllocator(Cfg cfg,
                          LivenessAnalysis.Result liveness,
                          AsyncSpillAnalysis.Result asyncSpills,
                          SsaDestruction.Result ssaDestruction) {
        this.cfg = cfg;
        this.liveness = liveness;
        this.asyncSpills = asyncSpills;
        this.ssaDestruction = ssaDestruction;
    }

    public Result allocate() {
        List<SsaValue> orderedValues = collectValues();
        StaticOperands staticOperands = collectStaticOperands(orderedValues);

        Map<SsaValue, Location> locations = new IdentityHashMap<>();
        List<SsaValue> localValues = new ArrayList<>();
        List<SsaValue> asyncFieldValues = new ArrayList<>();
        Set<SsaValue> stackValues = collectStackValues();

        Map<SsaValue, Integer> asyncFieldIds = assignAsyncFieldIds(orderedValues);
        for (SsaValue value : orderedValues) {
            Expr assign = value.getAssign();
            if (assign instanceof LoadRoot) {
                locations.put(value, RootFieldLocation.INSTANCE);
            } else if (assign instanceof LoadConst) {
                locations.put(value, staticOperands.locationOf((LoadConst) assign));
            } else if (stackValues.contains(value)) {
                locations.put(value, StackLocation.INSTANCE);
            } else if (asyncFieldIds.containsKey(value)) {
                int fieldId = asyncFieldIds.get(value);
                locations.put(value, new AsyncFieldLocation(fieldId, asyncFieldName(fieldId)));
                asyncFieldValues.add(value);
            } else {
                localValues.add(value);
            }
        }

        Map<SsaValue, Set<SsaValue>> graph = buildInterferenceGraph(localValues);
        Map<SsaValue, Integer> colors = colorGraph(localValues, graph);
        int colorCount = 0;
        for (Integer color : colors.values()) {
            colorCount = Math.max(colorCount, color + 1);
        }
        for (SsaValue value : localValues) {
            locations.put(value, new LocalLocation(colors.get(value) + 1));
        }

        int maxLocal = colorCount + 1;
        return new Result(locations, orderedValues, localValues, asyncFieldValues, maxLocal, staticOperands);
    }

    private Set<SsaValue> collectStackValues() {
        Set<SsaValue> values = newSet();
        Set<SsaValue> edgeCopyValues = collectEdgeCopyValues();
        for (Block block : cfg.getBlocks()) {
            List<Instruction> instructions = block.getInstructions();
            for (int i = 1; i < instructions.size(); i++) {
                Instruction instruction = instructions.get(i);
                Instruction producer = instructions.get(i - 1);
                if (!(producer instanceof Expr)) {
                    continue;
                }
                SsaValue value = ((Expr) producer).getResult();
                if (value.getUsedCount() != 1
                        || value.getUsed().get(0) != instruction
                        || edgeCopyValues.contains(value)
                        || asyncSpills.isSpilled(value)
                        || !stackableProducer(producer)) {
                    continue;
                }
                values.add(value);
            }
        }
        return values;
    }

    private Set<SsaValue> collectEdgeCopyValues() {
        Set<SsaValue> values = newSet();
        if (ssaDestruction == null) {
            return values;
        }
        for (SsaDestruction.EdgeCopy edgeCopy : ssaDestruction.getEdgeCopies()) {
            for (SsaDestruction.Move move : edgeCopy.getMoves()) {
                values.add(move.getSrc());
                values.add(move.getDst());
            }
        }
        return values;
    }

    private static boolean stackableProducer(Instruction instruction) {
        return instruction.canThrow() == Instruction.Throw.NOT
                && (instruction instanceof NewObj
                || instruction instanceof NewArr
                || instruction instanceof Binary
                || instruction instanceof Unary
                || instruction instanceof IndexGet
                || instruction instanceof PropGet
                || instruction instanceof IndexSet1
                || instruction instanceof PropSet1
                || instruction instanceof ExpandObj
                || instruction instanceof ExpandArr
                || instruction instanceof PushArr);
    }

    private List<SsaValue> collectValues() {
        List<SsaValue> values = new ArrayList<>();
        Set<SsaValue> seen = newSet();

        for (Block block : cfg.getBlocks()) {
            for (Phi phi : block.getPhiValues()) {
                addValue(values, seen, phi.getResult());
            }
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof Expr) {
                    addValue(values, seen, ((Expr) instruction).getResult());
                }
                addValues(values, seen, LivenessAnalysis.operandsOf(instruction));
            }
            for (Phi phi : block.getPhiValues()) {
                for (Phi.Case aCase : phi.getCases()) {
                    addValue(values, seen, aCase.value);
                }
            }
        }

        for (Instruction instruction : asyncSpills.getAsyncInstructions()) {
            addValues(values, seen, asyncSpills.getSpillValues(instruction));
        }
        for (SsaValue value : asyncSpills.getSpillValues()) {
            addValue(values, seen, value);
        }
        for (SsaDestruction.EdgeCopy edgeCopy : ssaDestruction.getEdgeCopies()) {
            for (SsaDestruction.Move move : edgeCopy.getMoves()) {
                addValue(values, seen, move.getDst());
                addValue(values, seen, move.getSrc());
            }
        }
        for (Block block : cfg.getBlocks()) {
            addValues(values, seen, liveness.liveIn(block));
            addValues(values, seen, liveness.liveOut(block));
            for (Instruction instruction : block.getInstructions()) {
                addValues(values, seen, liveness.liveBefore(instruction));
                addValues(values, seen, liveness.liveAfter(instruction));
            }
        }
        return values;
    }

    private StaticOperands collectStaticOperands(List<SsaValue> orderedValues) {
        StaticOperands.Builder builder = new StaticOperands.Builder();
        for (SsaValue value : orderedValues) {
            Expr assign = value.getAssign();
            if (assign instanceof LoadConst) {
                builder.addLiteral(((LoadConst) assign).getValueNode());
            }
        }
        for (Block block : cfg.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof CallConst) {
                    builder.addConstant(((CallConst) instruction).getConstant());
                } else if (instruction instanceof CallAsyncConst) {
                    builder.addAsyncConstant(((CallAsyncConst) instruction).getConstant());
                } else if (instruction instanceof CallFunc) {
                    builder.addFunction(((CallFunc) instruction).getFunction());
                } else if (instruction instanceof CallAsyncFunc) {
                    builder.addAsyncFunction(((CallAsyncFunc) instruction).getFunction());
                }
            }
        }
        return builder.build();
    }

    private Map<SsaValue, Integer> assignAsyncFieldIds(List<SsaValue> orderedValues) {
        Map<SsaValue, Integer> ids = new IdentityHashMap<>();
        for (SsaValue value : orderedValues) {
            Expr assign = value.getAssign();
            if (assign instanceof LoadRoot || assign instanceof LoadConst || !asyncSpills.isSpilled(value)) {
                continue;
            }
            ids.put(value, ids.size());
        }
        return ids;
    }

    private Map<SsaValue, Set<SsaValue>> buildInterferenceGraph(List<SsaValue> localValues) {
        Set<SsaValue> localSet = newSet(localValues);
        Map<SsaValue, Set<SsaValue>> graph = new IdentityHashMap<>();
        for (SsaValue value : localValues) {
            graph.put(value, newSet());
        }

        for (Block block : cfg.getBlocks()) {
            addClique(graph, localSet, liveness.liveIn(block));
            addClique(graph, localSet, liveness.liveOut(block));

            Set<SsaValue> entryLive = newSet(liveness.liveIn(block));
            for (Phi phi : block.getPhiValues()) {
                entryLive.add(phi.getResult());
            }
            addClique(graph, localSet, entryLive);

            for (Instruction instruction : block.getInstructions()) {
                addClique(graph, localSet, liveness.liveBefore(instruction));
                addClique(graph, localSet, liveness.liveAfter(instruction));
            }
        }
        return graph;
    }

    private Map<SsaValue, Integer> colorGraph(List<SsaValue> localValues, Map<SsaValue, Set<SsaValue>> graph) {
        Map<SsaValue, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < localValues.size(); i++) {
            order.put(localValues.get(i), i);
        }

        List<SsaValue> work = new ArrayList<>(localValues);
        work.sort(new Comparator<SsaValue>() {
            @Override
            public int compare(SsaValue left, SsaValue right) {
                int degree = graph.get(right).size() - graph.get(left).size();
                return degree != 0 ? degree : order.get(left) - order.get(right);
            }
        });

        Map<SsaValue, Integer> colors = new IdentityHashMap<>();
        for (SsaValue value : work) {
            Set<Integer> used = new java.util.HashSet<>();
            for (SsaValue neighbor : graph.get(value)) {
                Integer color = colors.get(neighbor);
                if (color != null) {
                    used.add(color);
                }
            }
            int color = 0;
            while (used.contains(color)) {
                color++;
            }
            colors.put(value, color);
        }
        return colors;
    }

    private static void addClique(Map<SsaValue, Set<SsaValue>> graph, Set<SsaValue> localSet, Collection<SsaValue> live) {
        List<SsaValue> locals = new ArrayList<>();
        for (SsaValue value : live) {
            if (localSet.contains(value)) {
                locals.add(value);
            }
        }
        for (int i = 0; i < locals.size(); i++) {
            SsaValue left = locals.get(i);
            for (int j = i + 1; j < locals.size(); j++) {
                SsaValue right = locals.get(j);
                if (left == right) {
                    continue;
                }
                graph.get(left).add(right);
                graph.get(right).add(left);
            }
        }
    }

    private static void addValues(List<SsaValue> values, Set<SsaValue> seen, Collection<SsaValue> additions) {
        for (SsaValue value : additions) {
            addValue(values, seen, value);
        }
    }

    private static void addValue(List<SsaValue> values, Set<SsaValue> seen, SsaValue value) {
        if (value != null && seen.add(value)) {
            values.add(value);
        }
    }

    private static String literalName(int id) {
        return "_LITERAL_" + id;
    }

    private static String asyncFieldName(int id) {
        return "_async_val_" + id;
    }

    private static <T> Set<T> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
    }

    private static <T> Set<T> newSet(Collection<T> values) {
        Set<T> set = newSet();
        set.addAll(values);
        return set;
    }

    public abstract static class Location {
        public enum Kind {
            LOCAL,
            STACK,
            ASYNC_FIELD,
            STATIC_LITERAL,
            ROOT_FIELD,
        }

        private final Kind kind;

        private Location(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }
    }

    public static final class LocalLocation extends Location {
        private final int slot;

        private LocalLocation(int slot) {
            super(Kind.LOCAL);
            this.slot = slot;
        }

        public int getSlot() {
            return slot;
        }
    }

    public static final class StackLocation extends Location {
        private static final StackLocation INSTANCE = new StackLocation();

        private StackLocation() {
            super(Kind.STACK);
        }
    }

    public static final class AsyncFieldLocation extends Location {
        private final int fieldId;
        private final String fieldName;

        private AsyncFieldLocation(int fieldId, String fieldName) {
            super(Kind.ASYNC_FIELD);
            this.fieldId = fieldId;
            this.fieldName = fieldName;
        }

        public int getFieldId() {
            return fieldId;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    public static final class StaticLiteralLocation extends Location {
        private final int literalId;
        private final String fieldName;
        private final String fieldDesc;
        private final boolean copyOnLoad;

        private StaticLiteralLocation(int literalId, String fieldName, String fieldDesc, boolean copyOnLoad) {
            super(Kind.STATIC_LITERAL);
            this.literalId = literalId;
            this.fieldName = fieldName;
            this.fieldDesc = fieldDesc;
            this.copyOnLoad = copyOnLoad;
        }

        public int getLiteralId() {
            return literalId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getFieldDesc() {
            return fieldDesc;
        }

        public boolean isCopyOnLoad() {
            return copyOnLoad;
        }
    }

    public static final class RootFieldLocation extends Location {
        private static final RootFieldLocation INSTANCE = new RootFieldLocation();

        private RootFieldLocation() {
            super(Kind.ROOT_FIELD);
        }
    }

    public static final class StaticOperands {
        private final List<LiteralField> literals;
        private final List<OperandField<Library.Constant>> constants;
        private final List<OperandField<Library.AsyncConstant>> asyncConstants;
        private final List<OperandField<Library.Function>> functions;
        private final List<OperandField<Library.AsyncFunction>> asyncFunctions;
        private final Map<JsonNode, LiteralField> literalByValue;

        private StaticOperands(List<LiteralField> literals,
                               List<OperandField<Library.Constant>> constants,
                               List<OperandField<Library.AsyncConstant>> asyncConstants,
                               List<OperandField<Library.Function>> functions,
                               List<OperandField<Library.AsyncFunction>> asyncFunctions,
                               Map<JsonNode, LiteralField> literalByValue) {
            this.literals = Collections.unmodifiableList(new ArrayList<>(literals));
            this.constants = Collections.unmodifiableList(new ArrayList<>(constants));
            this.asyncConstants = Collections.unmodifiableList(new ArrayList<>(asyncConstants));
            this.functions = Collections.unmodifiableList(new ArrayList<>(functions));
            this.asyncFunctions = Collections.unmodifiableList(new ArrayList<>(asyncFunctions));
            this.literalByValue = new IdentityHashMap<>(literalByValue);
        }

        public List<LiteralField> getLiterals() {
            return literals;
        }

        public List<OperandField<Library.Constant>> getConstants() {
            return constants;
        }

        public List<OperandField<Library.AsyncConstant>> getAsyncConstants() {
            return asyncConstants;
        }

        public List<OperandField<Library.Function>> getFunctions() {
            return functions;
        }

        public List<OperandField<Library.AsyncFunction>> getAsyncFunctions() {
            return asyncFunctions;
        }

        public Object[] initOperands() {
            Object[] operands = new Object[literals.size() + constants.size() + asyncConstants.size()
                    + functions.size() + asyncFunctions.size()];
            int idx = 0;
            for (LiteralField field : literals) {
                operands[idx++] = field.getValue();
            }
            for (OperandField<Library.Constant> field : constants) {
                operands[idx++] = field.getValue();
            }
            for (OperandField<Library.AsyncConstant> field : asyncConstants) {
                operands[idx++] = field.getValue();
            }
            for (OperandField<Library.Function> field : functions) {
                operands[idx++] = field.getValue();
            }
            for (OperandField<Library.AsyncFunction> field : asyncFunctions) {
                operands[idx++] = field.getValue();
            }
            return operands;
        }

        private StaticLiteralLocation locationOf(LoadConst loadConst) {
            LiteralField field = literalByValue.get(loadConst.getValueNode());
            if (field == null) {
                throw new IllegalStateException("[bug] missing literal field");
            }
            return field.getLocation();
        }

        private static class Builder {
            private final List<LiteralField> literals = new ArrayList<>();
            private final List<OperandField<Library.Constant>> constants = new ArrayList<>();
            private final List<OperandField<Library.AsyncConstant>> asyncConstants = new ArrayList<>();
            private final List<OperandField<Library.Function>> functions = new ArrayList<>();
            private final List<OperandField<Library.AsyncFunction>> asyncFunctions = new ArrayList<>();
            private final Map<JsonNode, LiteralField> literalByValue = new IdentityHashMap<>();
            private final Map<Library.Constant, OperandField<Library.Constant>> constantByValue = new IdentityHashMap<>();
            private final Map<Library.AsyncConstant, OperandField<Library.AsyncConstant>> asyncConstantByValue = new IdentityHashMap<>();
            private final Map<Library.Function, OperandField<Library.Function>> functionByValue = new IdentityHashMap<>();
            private final Map<Library.AsyncFunction, OperandField<Library.AsyncFunction>> asyncFunctionByValue = new IdentityHashMap<>();

            private void addLiteral(ValueNode value) {
                if (literalByValue.containsKey(value)) {
                    return;
                }
                int id = literals.size();
                boolean copyOnLoad = value.isContainerNode();
                String desc = copyOnLoad ? JSON_NODE_DESC : VALUE_NODE_DESC;
                StaticLiteralLocation location = new StaticLiteralLocation(id, literalName(id), desc, copyOnLoad);
                LiteralField field = new LiteralField(id, location.getFieldName(), desc, value, location);
                literals.add(field);
                literalByValue.put(value, field);
            }

            private void addConstant(Library.Constant value) {
                addOperand(value, constantByValue, constants, "_CONST_");
            }

            private void addAsyncConstant(Library.AsyncConstant value) {
                addOperand(value, asyncConstantByValue, asyncConstants, "_ASYNC_CONST_");
            }

            private void addFunction(Library.Function value) {
                addOperand(value, functionByValue, functions, "_FUNC_");
            }

            private void addAsyncFunction(Library.AsyncFunction value) {
                addOperand(value, asyncFunctionByValue, asyncFunctions, "_ASYNC_FUNC_");
            }

            private <T> void addOperand(T value, Map<T, OperandField<T>> byValue, List<OperandField<T>> fields, String prefix) {
                if (byValue.containsKey(value)) {
                    return;
                }
                int id = fields.size();
                OperandField<T> field = new OperandField<>(id, prefix + id, value);
                fields.add(field);
                byValue.put(value, field);
            }

            private StaticOperands build() {
                return new StaticOperands(literals, constants, asyncConstants, functions, asyncFunctions, literalByValue);
            }
        }
    }

    public static class OperandField<T> {
        private final int id;
        private final String fieldName;
        private final T value;

        private OperandField(int id, String fieldName, T value) {
            this.id = id;
            this.fieldName = fieldName;
            this.value = value;
        }

        public int getId() {
            return id;
        }

        public String getFieldName() {
            return fieldName;
        }

        public T getValue() {
            return value;
        }
    }

    public static final class LiteralField extends OperandField<JsonNode> {
        private final String fieldDesc;
        private final StaticLiteralLocation location;

        private LiteralField(int id, String fieldName, String fieldDesc, JsonNode value, StaticLiteralLocation location) {
            super(id, fieldName, value);
            this.fieldDesc = fieldDesc;
            this.location = location;
        }

        public String getFieldDesc() {
            return fieldDesc;
        }

        public StaticLiteralLocation getLocation() {
            return location;
        }
    }

    public static class Result {
        private final Map<SsaValue, Location> locations;
        private final List<SsaValue> orderedValues;
        private final List<SsaValue> localValues;
        private final List<SsaValue> asyncFieldValues;
        private final int maxLocal;
        private final StaticOperands staticOperands;

        private Result(Map<SsaValue, Location> locations,
                       List<SsaValue> orderedValues,
                       List<SsaValue> localValues,
                       List<SsaValue> asyncFieldValues,
                       int maxLocal,
                       StaticOperands staticOperands) {
            this.locations = new IdentityHashMap<>(locations);
            this.orderedValues = Collections.unmodifiableList(new ArrayList<>(orderedValues));
            this.localValues = Collections.unmodifiableList(new ArrayList<>(localValues));
            this.asyncFieldValues = Collections.unmodifiableList(new ArrayList<>(asyncFieldValues));
            this.maxLocal = maxLocal;
            this.staticOperands = staticOperands;
        }

        public Location locationOf(SsaValue value) {
            Location location = locations.get(value);
            if (location == null) {
                throw new IllegalArgumentException("unallocated value");
            }
            return location;
        }

        public List<SsaValue> orderedValues() {
            return orderedValues;
        }

        public List<SsaValue> localValues() {
            return localValues;
        }

        public List<SsaValue> asyncFieldValues() {
            return asyncFieldValues;
        }

        public int maxLocal() {
            return maxLocal;
        }

        public int firstTempLocal() {
            return maxLocal;
        }

        public StaticOperands staticOperands() {
            return staticOperands;
        }
    }
}
