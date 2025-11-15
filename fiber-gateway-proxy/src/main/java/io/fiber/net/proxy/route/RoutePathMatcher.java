package io.fiber.net.proxy.route;

import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.Predictions;
import io.fiber.net.common.utils.StringUtils;

import java.util.*;

public class RoutePathMatcher<H> {

    private static class NodePool {
        private final TreeMap<Integer, Integer> idxMap = new TreeMap<>();
        private Node[] nodes;

        private void put(Integer start, Integer end) {
            assert start >= 0 && start < end;
            assert end <= nodes.length;
            idxMap.put(start, end);
        }

        int alloc(int cap) {
            int m = Integer.MAX_VALUE;
            Map.Entry<Integer, Integer> selected = null;
            for (Map.Entry<Integer, Integer> entry : idxMap.entrySet()) {
                Integer key = entry.getKey();
                int i = entry.getValue() - key;
                if (i < cap) {
                    continue;
                }
                if (i == cap) {
                    idxMap.remove(key);
                    return key;
                }
                if (i < m) {
                    m = i;
                    selected = entry;
                }
            }
            if (selected != null) {
                Integer key = selected.getKey();
                Integer value = selected.getValue();
                idxMap.remove(key);
                idxMap.put(key + cap, value);
                return key;
            }


            int newLen = cap << 2;
            int offset;
            if (nodes == null) {
                offset = 0;
                nodes = new Node[newLen];
            } else {
                offset = nodes.length;
                nodes = Arrays.copyOf(nodes, offset + newLen);
            }
            put(offset + cap, offset + newLen);
            return offset;
        }

        void free(Integer start, Integer end) {
            Arrays.fill(nodes, start, end, null);
            Integer e = idxMap.get(end);
            if (e != null) {
                idxMap.remove(end);
                end = e;
            }
            Map.Entry<Integer, Integer> entry = idxMap.lowerEntry(start);
            if (entry != null && entry.getValue().intValue() == start) {
                start = entry.getKey();
            }
            put(start, end);
        }
    }

    static class Node {
        private static final int INIT_CAP = 8;

        private static int index(int hash, int len) {
            return hash & (len - 1);
        }

        private final byte[] name;
        private final int hash;
        private final String nameTxt;
        private String fullPath = "";
        private int childrenStart = -1;
        private int childrenEnd;
        private int childrenSize;
        private int placeholderStart = -1;
        private int placeholderEnd;
        private int placeholderSize;
        private int wildcardStart = -1;
        private int wildcardEnd;
        private int wildcardSize;
        private int id = -1;

        //
        int handlerIdxStart = -1;
        int handlerIdxEnd = -1;

        public Node(byte[] name, int hash) {
            this.name = name;
            this.hash = hash;
            this.nameTxt = name.length == 0 ? "" : new String(name);
        }

        private int expand(NodePool pool) {
            int oLen = childrenEnd - childrenStart, newLen = oLen << 1;
            int ns = pool.alloc(newLen);
            for (int i = childrenStart; i < childrenEnd; i++) {
                Node c = pool.nodes[i];
                if (c == null) {
                    continue;
                }
                int idx = index(c.hash, newLen);
                while (pool.nodes[idx + ns] != null) {
                    if (++idx == newLen) {
                        idx = 0;
                    }
                }
                pool.nodes[idx + ns] = c;
            }
            pool.free(childrenStart, childrenEnd);
            childrenStart = ns;
            childrenEnd = ns + newLen;
            return newLen;
        }

        Node addOrGetChildrenWithName(NodePool pool, byte[] cs, int s, int e, int hash) {
            int length;
            if (childrenStart == -1) {
                childrenStart = pool.alloc(INIT_CAP);
                length = INIT_CAP;
                childrenEnd = childrenStart + length;
            } else if ((childrenSize << 1) > (length = childrenEnd - childrenStart)) {
                length = expand(pool);
            }

            Node c;
            int idx;
            for (idx = index(hash, length); (c = pool.nodes[idx + childrenStart]) != null; ) {
                if (c.equalsArrRange(cs, s, e, hash)) {
                    return c;
                } else if (++idx >= length) {
                    idx = 0;
                }
            }
            childrenSize++;
            return pool.nodes[idx + childrenStart] = new Node(s == e
                    ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }

        public Node findChildWithName(Node[] ch, byte[] cs, int s, int e, int hash) {
            int cf = childrenStart, ce = childrenEnd;
            if (cf == -1) {
                return null;
            }
            int i = index(hash, ce - cf) + cf;
            Node n;
            while ((n = ch[i]) != null) {
                if (n.equalsArrRange(cs, s, e, hash)) {
                    return n;
                }
                if (++i == ce) {
                    i = cf;
                }
            }
            return null;
        }

        private boolean equalsArrRange(byte[] cs, int s, int e, int hash) {
            if (hash != this.hash) {
                return false;
            }
            byte[] t;
            int len;
            if ((len = (t = this.name).length) != e - s) {
                return false;
            }

            for (int i = 0; i < len; i++) {
                if (t[i] != (cs[s + i])) {
                    return false;
                }
            }
            return true;
        }

        Node addOrGetPlaceholder(NodePool pool, byte[] cs, int s, int e, int hash) {
            if (placeholderSize == 0) {
                placeholderStart = pool.alloc(4);
                placeholderEnd = placeholderStart + 4;
            } else {
                Node r;
                for (int i = placeholderStart; i < placeholderEnd; ++i) {
                    if ((r = pool.nodes[i]).equalsArrRange(cs, s, e, hash)) {
                        return r;
                    }
                }
                if (placeholderEnd - placeholderStart == placeholderSize) {
                    int alloc = pool.alloc(placeholderSize << 1);
                    for (int i = 0; i < placeholderSize; ++i) {
                        pool.nodes[alloc + i] = pool.nodes[i + placeholderStart];
                    }
                    pool.free(placeholderStart, placeholderEnd);
                    placeholderStart = alloc;
                    placeholderEnd = alloc + placeholderSize;
                }
            }
            return pool.nodes[placeholderStart + placeholderSize++] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }

        Node setWildcard(NodePool pool, byte[] cs, int s, int e, int hash) {
            if (this.wildcardSize == 0) {
                wildcardStart = pool.alloc(4);
                wildcardEnd = wildcardStart + 4;
            } else {
                Node r;
                for (int i = 0; i < wildcardSize; i++) {
                    if ((r = pool.nodes[wildcardStart + i]).equalsArrRange(cs, s, e, hash)) {
                        return r;
                    }
                }
                if (wildcardEnd - wildcardStart == wildcardSize) {
                    int alloc = pool.alloc(wildcardSize << 1);
                    for (int i = 0; i < wildcardSize; ++i) {
                        pool.nodes[alloc + i] = pool.nodes[i + wildcardStart];
                    }
                    pool.free(wildcardStart, wildcardEnd);
                    wildcardStart = alloc;
                    wildcardEnd = alloc + wildcardStart;
                }
            }

            return pool.nodes[wildcardStart + wildcardSize++] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }

        public String getName() {
            return nameTxt;
        }

        @Override
        public String toString() {
            return fullPath;
        }
    }


    public static <B, H> Builder<B, H> builder(RouteVarDefiner<B, H> routeDefiner) {
        return new Builder<>(routeDefiner);
    }


    public static class Builder<B, H> {
        private final Node root = new Node(CharArrUtil.EMPTY_BYTES, 0);
        private final NodePool pool = new NodePool();
        private final IdentityHashMap<Node, List<B>> cache = new IdentityHashMap<>();
        private final RouteVarDefiner<B, H> routeDefiner;
        private boolean complete;
        private int handlerSize;
        private int maxPathVarLength;
        private int currentId;

        public Builder(RouteVarDefiner<B, H> routeDefiner) {
            this.routeDefiner = routeDefiner;
        }

        public void addUrlHandler(String url, B builder) {
            if (complete) {
                throw new IllegalStateException("config completed");
            }

            Node node = addPath(root, url, builder);
            if (StringUtils.isEmpty(node.fullPath)) {
                node.fullPath = url;
            }
            if (node.id < 0) {
                node.id = currentId++;
            }
            handlerSize++;
            cache.computeIfAbsent(node, n -> new ArrayList<>()).add(builder);
        }

        private Node addPath(Node root, String path, B builder) {

            Node n = root;
            Predictions.assertTextNotEmpty(path, "empty path is not allowed");

            byte[] chars = CharArrUtil.toReadOnlyAsciiCharArr(path);
            int length = chars.length;
            if (length != path.length()) {
                throw new RouteConflictException("path is not ascii char???");
            }

            int pathVarIdx = 0;

            int h = 0;
            int c;
            int s = 0;
            int wild = 0;// 0 NONE 1 PLACEHOLDERS 2 wildcard
            for (int i = 0; i <= length; i++) {
                c = i < length ? chars[i] : 0;
                if (c == 0 || c == '/') {
                    Node cn;
                    if (wild == 2) {
                        if (c != 0) {
                            throw new RouteConflictException("wildcard occur in middle of path not allowed: " + path);
                        }
                        cn = n.setWildcard(pool, chars, s + 1, i, h);
                        if (s + 1 < i) {
                            routeDefiner.addPathVarDefiner(builder, cn.nameTxt, pathVarIdx++);
                        }
                    } else if (wild == 1) {
                        cn = n.addOrGetPlaceholder(pool, chars, s + 1, i, h);
                        if (s + 1 < i) {
                            routeDefiner.addPathVarDefiner(builder, cn.nameTxt, pathVarIdx++);
                        }
                    } else if (i > 0) {
                        cn = (s < i || s == length) ? n.addOrGetChildrenWithName(pool, chars, s, i, h) : n;
                    } else {
                        cn = root;
                    }
                    n = cn;
                    h = 0;
                    s = i + 1;
                    wild = 0;
                } else if (s != i || c != ':' && c != '*') {
                    h = h * 31 + c;
                } else {
                    wild = c == ':' ? 1 : 2;
                }
            }

            maxPathVarLength = Math.max(maxPathVarLength, pathVarIdx);
            return n;
        }

        @SuppressWarnings("unchecked")
        public RoutePathMatcher<H> build() {
            complete = true;
            H[] ns = (H[]) new Object[handlerSize];
            int i = 0;
            for (Map.Entry<Node, List<B>> entry : cache.entrySet()) {
                Node node = entry.getKey();
                node.handlerIdxStart = i;
                node.handlerIdxEnd = i + entry.getValue().size();
                for (B b : entry.getValue()) {
                    ns[i++] = routeDefiner.onRouteMount(node.id, b);
                }
            }
            assert i == handlerSize;
            i = 0;
            int size = sizeOfNodes(root);
            Node[] nodes = new Node[size];
            Queue<Node> q = new ArrayDeque<>();
            q.add(root);
            do {
                Node node = q.poll();
                assert node != null && i <= size;
                i = addChild(node, q, nodes, i);
            } while (!q.isEmpty());

            assert i == size;
            return new RoutePathMatcher<>(root, nodes, ns, maxPathVarLength);
        }

        private int addChild(Node node, Queue<Node> q, Node[] nodes, int idx) {
            if (node.childrenSize > 0) {
                System.arraycopy(pool.nodes, node.childrenStart, nodes, idx, node.childrenEnd - node.childrenStart);
                for (int i = node.childrenStart, e = node.childrenEnd; i < e; i++) {
                    Node n;
                    if ((n = pool.nodes[i]) != null) {
                        q.offer(n);
                    }
                }
                int o = idx;
                idx += node.childrenEnd - node.childrenStart;
                node.childrenStart = o;
                node.childrenEnd = idx;
            }
            if (node.placeholderSize > 0) {
                System.arraycopy(pool.nodes, node.placeholderStart, nodes, idx, node.placeholderSize);
                for (int i = node.placeholderStart, e = node.placeholderStart + node.placeholderSize; i < e; i++) {
                    q.offer(pool.nodes[i]);
                }
                int o = idx;
                idx += node.placeholderSize;
                node.placeholderStart = o;
                node.placeholderEnd = idx;
            }
            if (node.wildcardSize > 0) {
                System.arraycopy(pool.nodes, node.wildcardStart, nodes, idx, node.wildcardSize);
                for (int i = node.wildcardStart, e = node.wildcardStart + node.wildcardSize; i < e; i++) {
                    q.offer(pool.nodes[i]);
                }
                int o = idx;
                idx += node.wildcardSize;
                node.wildcardStart = o;
                node.wildcardEnd = idx;
            }
            return idx;
        }

        private int sizeOfNodes(Node node) {
            int r = 0;
            if (node.childrenSize > 0) {
                r += node.childrenEnd - node.childrenStart;
                for (int i = node.childrenStart, e = node.childrenEnd; i < e; i++) {
                    Node n;
                    if ((n = pool.nodes[i]) != null) {
                        r += sizeOfNodes(n);
                    }
                }
            }
            if (node.placeholderSize > 0) {
                r += node.placeholderSize;
                for (int i = node.placeholderStart, e = node.placeholderStart + node.placeholderSize; i < e; i++) {
                    r += sizeOfNodes(pool.nodes[i]);
                }
            }
            if (node.wildcardSize > 0) {
                r += node.wildcardSize;
                for (int i = node.wildcardStart, e = node.wildcardStart + node.wildcardSize; i < e; i++) {
                    r += sizeOfNodes(pool.nodes[i]);
                }
            }
            return r;
        }

    }


    private final Node root;
    private final Node[] nodes;
    private final H[] handlers;
    private final int maxPathVarLength;

    RoutePathMatcher(Node root, Node[] nodes, H[] handlers, int maxPathVarLength) {
        this.root = root;
        this.nodes = nodes;
        this.handlers = handlers;
        this.maxPathVarLength = maxPathVarLength;
    }

    public interface RouteVarDefiner<B, H> {
        void addPathVarDefiner(B builder, String varName, int idx);

        H onRouteMount(int routeNodeId, B builder) throws RouteConflictException;
    }

    private boolean exec(byte[] cs, int idx, Node n, MappingContext<H> context) {
        int length = cs.length;
        int i, h = 0, b = idx;
        for (i = b; i < length; i++) {
            byte ch;
            if ((ch = cs[i]) != '/') {
                h = 31 * h + ch;
            } else if (b < i) {// =/
                break;
            } else {
                b++;
            }
        }

        Node[] nodes = this.nodes;
        Node cn = n.findChildWithName(nodes, cs, b, i, h);
        boolean ends = i >= length;
        // end ...
        if ((cn != null)) {
            if (ends) {
                if (matchNode(cn, context)) {
                    return true;
                }
                // /a match /a/ and /a/*
                if (i > b && exec(cs, i, cn, context)) {
                    return true;
                }
            } else if (exec(cs, i, cn, context)) {
                return true;
            }
        }
        // 占为符 匹配 1 . / 结尾 匹配.
        if ((i > b || b > idx) && n.placeholderSize != 0) {
            String value = null;
            for (int pi = n.placeholderStart, phe = n.placeholderEnd; pi < phe; pi++) {
                Node c = nodes[pi];
                boolean hasParam = StringUtils.isNotEmpty(c.nameTxt);
                if (hasParam) {
                    if (value == null) {
                        value = RoutePathMatcher.ofValue(cs, b, i - b);
                    }
                    context.addPathVar(c.nameTxt, value);
                }
                if (ends) {
                    if (matchNode(c, context)) {
                        return true;
                    }
                    // /a match /:p/ and /:p/*
                    if (i > b && exec(cs, i, c, context)) {
                        return true;
                    }
                } else if (exec(cs, i, c, context)) {
                    return true;
                }
                if (hasParam) {
                    context.popPathVar();
                }
            }
        }

        // 统配符 * ,匹配 0-N
        if (n.wildcardSize != 0) {
            String value = null;
            for (int pi = n.wildcardStart, wce = n.wildcardEnd; pi < wce; pi++) {
                Node c = nodes[pi];
                boolean hasParam = StringUtils.isNotEmpty(c.nameTxt);
                if (hasParam) {
                    if (value == null) {
                        value = RoutePathMatcher.ofValue(cs, b, length - b);
                    }
                    context.addPathVar(c.nameTxt, value);
                }
                if (matchNode(c, context)) {
                    return true;
                }
                if (hasParam) {
                    context.popPathVar();
                }
            }
        }

        return i <= b && b > idx && matchNode(n, context);  // b > idx match /a/b/  -> try parent /a/b
    }

    public boolean matchPath(String path, MappingContext<H> context) {
        byte[] cs = CharArrUtil.toReadOnlyAsciiCharArr(path);
        return exec(cs, 0, root, context);
    }

    public int getMaxPathVarLength() {
        return maxPathVarLength;
    }

    public interface MappingContext<H> {
        boolean matched(int nodeId, H handler);

        void addPathVar(String var, String value);

        void popPathVar();
    }

    private static String ofValue(byte[] cs, int idx, int len) {
        if (len <= 0 || cs.length <= idx) {
            return "";
        }
        return new String(cs, idx, len);
    }

    private boolean matchNode(Node node, MappingContext<H> context) {
        int s = node.handlerIdxStart, e = node.handlerIdxEnd, nodeId = node.id;
        if (s < 0) {
            return false;
        }
        H[] handlers = this.handlers;
        for (int i = s; i < e; i++) {
            if (context.matched(nodeId, handlers[i])) {
                return true;
            }
        }
        return false;
    }

}