package io.fiber.net.common.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class Cidr {
    public static final Cidr[] EMPTY = new Cidr[0];

    public abstract int getMask();

    public final boolean match(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return match(ip, 0, ip.length());
    }

    public final boolean match(String ip, int pos, int limit) {
        byte[] bytes = IpUtils.parseIp(ip, pos, limit);
        if (bytes == null) {
            return false;
        }
        return match(bytes);
    }

    public abstract boolean match(Cidr other);

    protected abstract boolean match(byte[] bytes);

    public abstract boolean contains(Cidr sub);

    public static class V4 extends Cidr {
        private final long ip;
        private final long mask;
        private final int maskBits;

        V4(long ip, int mask) {
            this.maskBits = mask;
            this.mask = prefixMask32(mask);
            this.ip = ip & this.mask;
        }

        @Override
        public int getMask() {
            return maskBits;
        }

        @Override
        public boolean match(Cidr other) {
            if (other instanceof V4) {
                V4 v4 = (V4) other;
                return ip == (v4.ip & mask);
            }
            return false;
        }

        @Override
        protected boolean match(byte[] bytes) {
            return ip == (Cidr.getV(bytes) & mask);
        }

        @Override
        public boolean contains(Cidr sub) {
            if (sub instanceof V4) {
                V4 v4 = (V4) sub;
                return maskBits <= v4.maskBits && ip == (v4.ip & mask);
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            V4 v4 = (V4) o;
            return ip == v4.ip &&
                    mask == v4.mask;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, mask);
        }
    }

    public static class V6 extends Cidr {
        private final long hMask, high;
        private final long lMask, low;
        private final int maskBits;

        // high 是 字符串 序列在前面的数字
        V6(long low, long high, int mask) {
            this.maskBits = mask;
            this.hMask = mask >= 64 ? -1L : prefixMask64(mask);
            this.high = high & hMask;

            this.lMask = mask <= 64 ? 0L : prefixMask64(mask - 64);
            this.low = low & lMask;
        }

        @Override
        public int getMask() {
            return maskBits;
        }

        @Override
        public boolean match(Cidr other) {
            if (other instanceof V6) {
                V6 v6 = (V6) other;
                return high == (hMask & v6.high)
                        && low == (lMask & v6.low);
            }
            return false;
        }

        @Override
        protected boolean match(byte[] bytes) {
            return high == (hMask & Cidr.getHigh(bytes))
                    && low == (lMask & Cidr.getLow(bytes));
        }

        @Override
        public boolean contains(Cidr sub) {
            if (sub instanceof V6) {
                V6 v6 = (V6) sub;
                if (maskBits > v6.maskBits) {
                    return false;
                }
                return high == (hMask & v6.high) && low == (lMask & v6.low);
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            V6 v6 = (V6) o;
            return hMask == v6.hMask &&
                    high == v6.high &&
                    lMask == v6.lMask &&
                    low == v6.low;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hMask, high, lMask, low);
        }
    }

    public static Cidr parse(String s) {
        if (StringUtils.isEmpty(s)) {
            return null;
        }
        return parse(s, 0, s.length());
    }

    public static Cidr[] parseList(Collection<String> cidrText) {
        if (CollectionUtils.isEmpty(cidrText)) {
            return EMPTY;
        }

        List<Cidr> cidrs = new ArrayList<>(cidrText.size());
        for (String s : cidrText) {
            Cidr cidr = parse(s);
            if (cidr == null) {
                throw new IllegalStateException("invalid cidr: " + s);
            }
            cidrs.add(cidr);
        }
        int size = cidrs.size();
        if (size == 1) {
            return new Cidr[]{cidrs.get(0)};
        }

        boolean[] rms = new boolean[size];

        int rm = 0;
        for (int i = 0; i < size; i++) {
            if (rms[i]) {
                continue;
            }
            Cidr left = cidrs.get(i);
            for (int j = i + 1; j < size; j++) {
                if (rms[j]) {
                    continue;
                }
                Cidr right = cidrs.get(j);
                if (left.contains(right)) {
                    rm++;
                    rms[j] = true;
                    continue;
                }

                if (right.contains(left)) {
                    rm++;
                    rms[i] = true;
                    break;
                }
            }
        }
        if (rm == size) {
            throw new IllegalStateException("[bug]empty cidr???");
        }

        Cidr[] ret = new Cidr[size - rm];
        for (int i = 0, j = 0; i < size; i++) {
            if (rms[i]) {
                continue;
            }
            ret[j++] = cidrs.get(i);
        }
        return ret;
    }

    public static boolean matchAny(Cidr[] cidrs, String s) {
        if (StringUtils.isEmpty(s)) {
            return false;
        }
        return matchAny(cidrs, s, 0, s.length());
    }

    public static boolean matchAny(Cidr[] cidrs, String s, int pos, int limit) {
        if (ArrayUtils.isEmpty(cidrs)) {
            return false;
        }
        Cidr cidr = parse(s, pos, limit);
        if (cidr == null) {
            return false;
        }
        for (Cidr n : cidrs) {
            if (n.match(cidr)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchAny(Cidr[] cidrs, Cidr target) {
        if (ArrayUtils.isNotEmpty(cidrs) && target != null) {
            for (Cidr n : cidrs) {
                if (n.match(target)) {
                    return true;
                }
            }
        }
        return false;
    }


    // parseCIDR parses s as a CIDR notation IP address and prefix length,
    // like "192.0.2.0/24" or "2001:db8::/32", as defined in
    // RFC 4632 and RFC 4291.
    //
    // It returns the IP address and the network implied by the IP and
    // prefix length.
    // For example, ParseCIDR("192.0.2.1/24") returns the IP address
    // 192.0.2.1 and the network 192.0.2.0/24.
    public static Cidr parse(String s, int pos, int limit) {
        int i = s.indexOf('/', pos);
        if (i < 0 || i >= limit) {
            byte[] bytes = IpUtils.parseIp(s, pos, limit);
            if (bytes == null) {
                return null;
            }
            return getCidr(bytes, bytes.length * 8);
        } else {
            byte[] bytes = IpUtils.parseIp(s, pos, Math.min(i, limit));
            if (bytes == null) {
                return null;
            }
            int mask;
            try {
                mask = Integer.parseUnsignedInt(s.substring(i + 1, limit));
                if (mask < 0 || mask > bytes.length * 8) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
            return getCidr(bytes, mask);
        }
    }


    private static Cidr getCidr(byte[] bytes, int mask) {
        if (bytes.length == 4) {
            return new V4(getV(bytes), mask);
        } else {
            long high = getHigh(bytes);
            long low = getLow(bytes);
            return new V6(low, high, mask);
        }
    }

    private static long getV(byte[] bytes) {
        return ((bytes[0] & 0xffL) << 24)
                | ((bytes[1] & 0xffL) << 16)
                | ((bytes[2] & 0xffL) << 8)
                | (bytes[3] & 0xffL);
    }

    private static long getLow(byte[] bytes) {
        return ((bytes[8] & 0xffL) << 56)
                | ((bytes[9] & 0xffL) << 48)
                | ((bytes[10] & 0xffL) << 40)
                | ((bytes[11] & 0xffL) << 32)
                | ((bytes[12] & 0xffL) << 24)
                | ((bytes[13] & 0xffL) << 16)
                | ((bytes[14] & 0xffL) << 8)
                | (bytes[15] & 0xffL);
    }

    private static long getHigh(byte[] bytes) {
        return ((bytes[0] & 0xffL) << 56)
                | ((bytes[1] & 0xffL) << 48)
                | ((bytes[2] & 0xffL) << 40)
                | ((bytes[3] & 0xffL) << 32)
                | ((bytes[4] & 0xffL) << 24)
                | ((bytes[5] & 0xffL) << 16)
                | ((bytes[6] & 0xffL) << 8)
                | (bytes[7] & 0xffL);
    }

    private static long prefixMask32(int bits) {
        return (0xffff_ffffL << (32 - bits)) & 0xffff_ffffL;
    }

    private static long prefixMask64(int bits) {
        if (bits <= 0) {
            return 0L;
        }
        if (bits >= 64) {
            return -1L;
        }
        return -1L << (64 - bits);
    }
}
