package io.fiber.net.script.parse.ir;

class CacheVarName {
    private final String prefix;
    private final String[] cache;

    CacheVarName(String prefix, int cacheSize) {
        this.prefix = prefix;
        cache = new String[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            cache[i] = generateName(i);
        }
    }

    String getNameById(int idx) {
        if (idx >= cache.length) {
            return generateName(idx);
        }
        return cache[idx];
    }

    private String generateName(int idx) {
        return prefix + idx;
    }

}
