package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.MissingNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.Assert;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.server.HttpExchange;

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
        private Node[] children;
        private int childrenSize;
        private Node[] placeholders;
        private int placeholderSize;
        private Node wildcard;

        //
        int handlerIdxStart = -1;
        int handlerIdxEnd = -1;
        int handlerMask;

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
                if (t[i] != cs[s + i]) {
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
            if (this.wildcard != null) {
                if (!this.wildcard.equalsArrRange(cs, s, e, hash)) {// 通配符不允许有两个值。
                    throw new RouteConflictException("wildcard exists: " + this.wildcard);
                }
                return this.wildcard;
            }
            return this.wildcard = new Node(s == e ? CharArrUtil.EMPTY_BYTES : Arrays.copyOfRange(cs, s, e), hash);
        }


        public Node getWildcard() {
            return wildcard;
        }

        public Iterator<Node> placeholders() {
            if (placeholderSize == 0) {
                return Collections.emptyIterator();
            }
            return new ChildrenItr();
        }

        private class ChildrenItr implements Iterator<Node> {
            private int c;

            @Override
            public boolean hasNext() {
                return c < placeholderSize;
            }

            @Override
            public Node next() {
                return placeholders[c++];
            }
        }

        public String getName() {
            return nameTxt;
        }

        @Override
        public String toString() {
            return nameTxt;
        }
    }


    public static <H> Builder<H> builder() {
        return new Builder<>();
    }

    private static final HttpMethod[] METHODS = HttpMethod.values();
    private static final int METHOD_CELL = METHODS.length + 1;
    private static final int ALL_METHOD_BIT = 1 << METHODS.length;
    private static final int ALL_METHOD_MASK = ALL_METHOD_BIT - 1;


    public static class Builder<H> {
        private final Node root = new Node(CharArrUtil.EMPTY_BYTES, 1);
        private final List<Node> nodes = new ArrayList<>();
        private boolean complete;
        private H[] scripts;
        private int cIdx;

        @SuppressWarnings("unchecked")
        private void expandScripts() {
            if (scripts == null || scripts.length <= cIdx) {
                scripts = scripts == null ? (H[]) new Object[16 * METHOD_CELL] : Arrays.copyOf(scripts, cIdx << 1);
            }
        }

        public void addUrlHandler(HttpMethod method, String url, H handler) {
            if (complete) {
                throw new IllegalStateException("config completed");
            }

            int mIdx = method == null ? METHODS.length : method.ordinal();

            Node node = addPath(root, url);
            if (node.handlerIdxStart == -1) {
                nodes.add(node);
                expandScripts();
                node.handlerIdxStart = cIdx;
                cIdx += METHOD_CELL;
            }

            int i = node.handlerIdxStart + mIdx;
            if (scripts[i] != null) {
                throw new RouteConflictException("router exists:" + method + "  " + url);
            }
            node.handlerIdxEnd = Math.max(node.handlerIdxEnd, i);
            node.handlerMask |= 1 << mIdx;
            scripts[i] = handler;
        }

        private Node addPath(Node root, String path) {
            int h = 1;
            if (StringUtils.isEmpty(path)) {
                return root.addOrGetChildrenWithName(CharArrUtil.EMPTY_BYTES, 0, 0, 1);
            }

            Node n = root;
            byte[] chars = CharArrUtil.toByteArr(path);
            int i = 0;
            int length = chars.length;
            if (length != path.length()) {
                throw new RouteConflictException("path is not ascii char???");
            }

            byte c;
            int s = 0;
            int wild = 0;// 0 NONE 1 PLACEHOLDERS 2 wildcard
            for (; i <= length; i++) {
                c = i < length ? chars[i] : 0;
                if (c == 0 || c == '/') {
                    Node cn;
                    if (wild == 2) {
                        if (c != 0) {
                            throw new RouteConflictException("wildcard occur in middle of path not allowed: " + path);
                        }
                        cn = n.setWildcard(chars, s + 1, i, h);
                    } else if (wild == 1) {
                        cn = n.addOrGetPlaceholder(chars, s + 1, i, h);
                    } else if (i > 0) {
                        cn = n.addOrGetChildrenWithName(chars, s, i, h);
                    } else {
                        cn = root;
                    }
                    n = cn;
                    h = 1;
                    s = i + 1;
                    wild = 0;
                } else if (s != i || c != ':' && c != '*') {
                    h = h * 31 + c;
                } else {
                    wild = c == ':' ? 1 : 2;
                }
            }

            return n;
        }

        @SuppressWarnings("unchecked")
        public RoutePathMatcher<H> build() {
            complete = true;

            int len = 0;
            for (Node node : nodes) {
                if ((node.handlerMask & ALL_METHOD_BIT) != 0) {
                    Assert.isTrue(node.handlerIdxEnd == node.handlerIdxStart + METHODS.length);
                    len += METHODS.length;
                } else {
                    int cl = node.handlerIdxEnd - node.handlerIdxStart + 1;
                    Assert.isTrue(cl > 0);
                    len += cl;
                }
            }
            Assert.isTrue(len > 0);

            H[] ns = (H[]) new Object[len];
            int p = 0;
            for (Node node : nodes) {
                int currentPos = p;
                if ((node.handlerMask & ALL_METHOD_BIT) != 0) {
                    H am = scripts[node.handlerIdxStart + METHODS.length];
                    for (int i = node.handlerIdxStart; i < node.handlerIdxEnd; i++) {
                        H h = scripts[i];
                        if (h == null) {
                            h = am;
                        }
                        ns[p++] = h;
                    }
                    node.handlerMask = ALL_METHOD_MASK;
                } else {
                    for (int i = node.handlerIdxStart; i <= node.handlerIdxEnd; i++) {
                        ns[p++] = scripts[i];
                    }
                }
                node.handlerIdxStart = currentPos;
                node.handlerIdxEnd = p;
            }
            return new RoutePathMatcher<>(root, ns);
        }

    }


    private final Node root;
    private final H[] handlers;

    RoutePathMatcher(Node root, H[] handlers) {
        this.root = root;
        this.handlers = handlers;
    }


    private static <H> int exec(byte[] cs, int idx, Node n, int mBit, MappingResult<H> context) {
        int length = cs.length;

        int i, h = 1, matched;
        byte ch;
        Iterator<Node> placeholders;
        for (i = idx; i <= length; i++) {
            if (i < length && (ch = cs[i]) != '/') {
                h = 31 * h + ch;
                continue;
            }

            Node cn = n.findChildWithName(cs, idx, i, h);
            if (cn != null) {
                idx = i + 1;
                n = cn;
                h = 1;
                continue;
            }

            placeholders = n.placeholders();
            if (placeholders.hasNext()) {
                Node c;
                do {
                    c = placeholders.next();
                    if ((matched = exec(cs, i + 1, c, mBit, context)) >= 0) {
                        context.addParam(c.getName(), new String(cs, idx, i - idx));
                        return matched;
                    }
                } while (placeholders.hasNext());
            }

            Node wildcard;
            if ((wildcard = n.getWildcard()) != null) {
                if ((wildcard.handlerMask & (1 << mBit)) != 0) {
                    context.addParam(wildcard.getName(), new String(cs, idx, length - idx));
                    return wildcard.handlerIdxStart + mBit;
                }
            }

            return -1;
        }

        return (n.handlerMask & (1 << mBit)) != 0 ? (n.handlerIdxStart + mBit) : -1;
    }

    public MappingResult<H> matchPath(HttpExchange exchange) {
        MappingResult<H> result = new MappingResult<>();

        byte[] cs = CharArrUtil.toByteArr(exchange.getPath());
        int idx = 0;
        if (cs[0] == '/') {
            idx = 1;
        }
        int matched = exec(cs, idx, root, exchange.getRequestMethod().ordinal(), result);
        if (matched >= 0) {
            result.handler = handlers[matched];
        }
        return result;
    }

    public static class MappingResult<H> {
        private H handler;
        private Map<String, JsonNode> map;

        public H getHandler() {
            return handler;
        }

        void addParam(String var, String val) {
            if (map == null) {
                map = new HashMap<>();
            }
            map.put(var, TextNode.valueOf(val));
        }

        public Map<String, JsonNode> getMap() {
            return map == null ? Collections.emptyMap() : map;
        }

        public JsonNode getVar(String var) {
            if (map == null) {
                return MissingNode.getInstance();
            }
            return map.getOrDefault(var, MissingNode.getInstance());
        }
    }

}