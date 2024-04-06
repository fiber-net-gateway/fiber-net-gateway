package io.fiber.net.script.parse.ir;

import java.util.*;

class ExtOperand<T> {
    static class OperandKey<T> {
        private final T key;
        private final int id;

        OperandKey(T key, int id) {
            this.key = key;
            this.id = id;
        }

        T getKey() {
            return key;
        }

        int getId() {
            return id;
        }

        static <T> OperandKey<T> of(T t, int id) {
            return new OperandKey<>(t, id);
        }
    }

    private final Map<Integer, Integer> idxIdMap = new HashMap<>();
    private final List<OperandKey<T>> keyList = new ArrayList<>();

    int addIdx(int idx, T operand) {
        Integer id = idxIdMap.get(idx);
        if (id != null) {
            return id;
        }
        id = keyList.size();
        keyList.add(OperandKey.of(operand, id));
        idxIdMap.put(idx, id);
        return id;
    }

    public List<OperandKey<T>> getKeyList() {
        return keyList;
    }
}
