package io.fiber.net.proxy;


import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.Predictions;

import java.util.Arrays;

/**
 * 二次线性探测法 hash。case insensitive
 */
public class WildHostNode {

    static final int INIT_CAP = 16;

    private static int idx(int hash, int len) {
        return hash & (len - 1);
    }

    private static int hash(int h) {
        return h ^ (h >>> 16);
    }

    private final int hash;
    private final byte[] seg;

    private WildHostNode[] children;
    private int size;
    private boolean wildcard;
    private boolean end;

    public WildHostNode() {
        this.hash = 1;
        this.seg = CharArrUtil.EMPTY_BYTES;
    }

    public WildHostNode(int hash, byte[] seg) {
        this.hash = hash;
        this.seg = seg;
    }

    public void addDomainPattern(String domainPattern) {
        addDomainPattern(domainPattern.getBytes());
    }

    private void addDomainPattern(byte[] cs) {
        int last = cs.length;
        WildHostNode n = this;
        if (n.wildcard) {
            return;
        }
        int i, h = 1;
        for (i = last - 1; i >= 0; i--) {
            int c;
            if ((c = (cs[i] |= 0x20)) == '.') {
                n = n.add(cs, i + 1, last, hash(h));
                assert n != null;
                if (n.wildcard) {
                    return;
                }
                last = i;
                h = 1;
            } else if (c == '*') {
                n.markWild();
                Predictions.assertTrue(i == 0, "* must be prefix for host");
                Predictions.assertTrue(last == 1, "*.xxx.com is supported only");
                return;
            } else {
                h = 31 * h + c;
            }
        }
        n = n.add(cs, 0, last, hash(h));
        assert n != null;
        n.end = true;
    }

    private void markWild() {
        wildcard = true;
        size = 0;
        children = null;
    }

    private WildHostNode[] expand() {
        WildHostNode[] old = this.children;
        int oLen = old.length, newLen = oLen << 1;
        WildHostNode[] nc = new WildHostNode[newLen];
        for (WildHostNode c : old) {
            if (c == null) {
                continue;
            }
            int idx = idx(c.hash, newLen);
            while (nc[idx] != null) {
                if (++idx == newLen) {
                    idx = 0;
                }
            }
            nc[idx] = c;
        }
        return this.children = nc;
    }

    public boolean matchHost(String host) {
        byte[] chars = CharArrUtil.toReadOnlyAsciiCharArr(host);
        return matchHost(chars, 0, chars.length);
    }

    public boolean matchHost(String host, int from) {
        byte[] chars = CharArrUtil.toReadOnlyAsciiCharArr(host);
        return matchHost(chars, from, chars.length);
    }

    boolean matchHost(byte[] cs, int from, int to) {
        WildHostNode n = this;
        if (n.wildcard) {
            return true;
        }
        int len = to - from;
        if (len > cs.length || len <= 0 || from < 0 || to > cs.length) {
            throw new IllegalArgumentException(String.format("host range error,length=%d, from=%d  to=%d", cs.length, from, to));
        }

        int last = to;
        int i, h = 1;
        for (i = to - 1; i >= from; i--) {
            int c;
            if ((c = (cs[i] | 0x20)) == '.') {
                n = n.find(cs, i + 1, last, hash(h));
                if (n == null) {
                    return false;
                } else if (n.wildcard) {
                    return true;
                }
                last = i;
                h = 1;
            } else {
                h = 31 * h + c;
            }
        }
        n = n.find(cs, from, last, hash(h));
        return n != null && (n.wildcard || n.end);
    }

    private WildHostNode find(byte[] cs, int s, int e, int hash) {
        WildHostNode[] children;
        if ((children = this.children) == null) {
            return null;
        }
        int len;
        int i = idx(hash, len = children.length);
        WildHostNode n;
        while ((n = children[i]) != null) {
            if (n.equalsArrRange(cs, s, e, hash)) {
                return n;
            }
            if (++i == len) {
                i = 0;
            }
        }
        return null;
    }

    private WildHostNode add(byte[] cs, int s, int e, int hash) {
        if (wildcard) {
            return null;
        }
        WildHostNode[] children;
        int length;
        if ((children = this.children) == null) {
            children = this.children = new WildHostNode[length = INIT_CAP];
        } else if ((size << 1) > (length = children.length)) {
            children = expand();
            length = children.length;
        }

        WildHostNode c;
        int idx;
        for (idx = idx(hash, length); (c = children[idx]) != null; ) {
            if (c.equalsArrRange(cs, s, e, hash)) {
                return c;
            } else if (++idx >= length) {
                idx = 0;
            }
        }
        size++;
        return children[idx] = new WildHostNode(hash, Arrays.copyOfRange(cs, s, e));
    }

    private boolean equalsArrRange(byte[] cs, int s, int e, int hash) {
        if (hash != this.hash) {
            return false;
        }
        byte[] t;
        int len;
        if ((len = (t = this.seg).length) != e - s) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (t[i] != (cs[s + i] | 0x20)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WildHostNode node = (WildHostNode) o;
        return hash == node.hash && Arrays.equals(seg, node.seg);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}