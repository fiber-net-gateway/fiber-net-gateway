package io.fiber.net.proxy;

import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.Predictions;
import io.fiber.net.common.utils.StringUtils;

import java.util.*;

public class RoutePathMatcher<H> {

    static class Node {
        private static final int INIT_CAP = 16;

        private static int index(int hash, int len) {
            return hash & (len - 1);
        }

        private final byte[] name;
        private final int hash;
        private final String nameTxt;
        private String fullPath = "";
        private Node[] children;
        private int childrenSize;
        private Node[] placeholders;
        private int placeholderSize;
        private Node[] wildcard;
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

        private Node[] expand() {
            Node[] old = this.children;
            int oLen = old.length, newLen = oLen << 1;
            Node[] nc = new Node[newLen];
            for (Node c : old) {
                if (c == null) {
                    continue;
                }
                int idx = index(c.hash, newLen);
                while (nc[idx] != null) {
                    if (++idx == newLen) {
                        idx = 0;
                    }
                }
                nc[idx] = c;
            }
            return this.children = nc;
        }

        public Node addOrGetChildrenWithName(byte[] cs, int s, int e, int hash) {
            Node[] ch;
            int length;
            if ((ch = this.children) == null) {
                ch = this.children = new Node[length = INIT_CAP];
            } else if ((childrenSize << 1) > (length = ch.length)) {
                length = (ch = expand()).length;
            }

            Node c;
            int idx;
            for (idx = index(hash, length); (c = ch[idx]) != null; ) {
                if (c.equalsArrRange(cs, s, e, hash)) {
                    return c;
                } else if (++idx >= length) {
                    idx = 0;
                }
            }
            childrenSize++;
            return ch[idx] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }

        public Node findChildWithName(byte[] cs, int s, int e, int hash) {
            Node[] ch;
            if ((ch = this.children) == null) {
                return null;
            }
            int len;
            int i = index(hash, len = children.length);
            Node n;
            while ((n = ch[i]) != null) {
                if (n.equalsArrRange(cs, s, e, hash)) {
                    return n;
                }
                if (++i == len) {
                    i = 0;
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

        public Node addOrGetPlaceholder(byte[] cs, int s, int e, int hash) {
            Node[] phs;
            Node r;
            int ps;
            if ((ps = placeholderSize) == 0) {
                phs = placeholders = new Node[4];
                placeholderSize = 1;
                return phs[0] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
            }

            phs = placeholders;
            for (int i = 0; i < ps; ++i) {
                if ((r = phs[i]).equalsArrRange(cs, s, e, hash)) {
                    return r;
                }
            }
            if (ps == phs.length) {
                phs = placeholders = Arrays.copyOf(phs, phs.length << 1);
            }
            return phs[placeholderSize++] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }

        public Node setWildcard(byte[] cs, int s, int e, int hash) {
            if (this.wildcard == null) {
                wildcard = new Node[4];
            } else {
                for (int i = 0; i < wildcardSize; i++) {
                    if (wildcard[i].equalsArrRange(cs, s, e, hash)) {
                        return wildcard[i];
                    }
                }
                if (wildcardSize == wildcard.length) {
                    wildcard = Arrays.copyOf(wildcard, wildcardSize << 1);
                }
            }

            return wildcard[wildcardSize++] = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }


        public Node[] getWildcard() {
            return wildcard;
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
                        cn = n.setWildcard(chars, s + 1, i, h);
                        if (s + 1 < i) {
                            routeDefiner.addPathVarDefiner(builder, cn.nameTxt, pathVarIdx++);
                        }
                    } else if (wild == 1) {
                        cn = n.addOrGetPlaceholder(chars, s + 1, i, h);
                        if (s + 1 < i) {
                            routeDefiner.addPathVarDefiner(builder, cn.nameTxt, pathVarIdx++);
                        }
                    } else if (i > 0) {
                        cn = (s < i || s == length) ? n.addOrGetChildrenWithName(chars, s, i, h) : n;
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
            return new RoutePathMatcher<>(root, ns, maxPathVarLength);
        }

    }


    private final Node root;
    private final H[] handlers;
    private final int maxPathVarLength;

    RoutePathMatcher(Node root, H[] handlers, int maxPathVarLength) {
        this.root = root;
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

        int phs;
        Node cn = n.findChildWithName(cs, b, i, h);
        boolean ends = i >= length;
        // end ...
        if ((cn != null)) {
            if (ends) {
                if (matchNode(cn, context)) {
                    return true;
                }
            } else if (exec(cs, i, cn, context)) {
                return true;
            }
        }
        // 占为符 匹配 1 . / 结尾 匹配.
        if ((i > b || b > idx) && (phs = n.placeholderSize) != 0) {
            String value = null;
            Node[] placeholders = n.placeholders;
            for (int pi = 0; pi < phs; pi++) {
                Node c = placeholders[pi];
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
                } else if (exec(cs, i, c, context)) {
                    return true;
                }
                if (hasParam) {
                    context.popPathVar();
                }
            }
        }

        // 统配符 * ,匹配 0-N
        if ((phs = n.wildcardSize) != 0) {
            String value = null;
            Node[] wildcard = n.wildcard;
            for (int pi = 0; pi < phs; pi++) {
                Node c = wildcard[pi];
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
        // /a match /a/ and /a/*
        if (i > b) {
            return cn != null && ends && exec(cs, i, cn, context);
        } else {
            return b > idx && matchNode(n, context);  // b > idx match /a/b/  -> try parent /a/b
        }
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