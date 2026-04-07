package io.fiber.net.proxy.route;

import java.util.Arrays;
import java.util.function.Consumer;

public class ConstPackage {
    private final VarType type;
    private VarConst[] cs;
    private int length;

    public ConstPackage(VarType type) {
        this.type = type;
    }

    public VarType getType() {
        return type;
    }

    public VarConst getOrCreate(String name) {
        byte[] data = name.getBytes();
        VarConst varConst = get(data, 0, data.length);
        if (varConst == null) {
            rehash();

            int h = 1;
            for (byte datum : data) {
                h = h * 31 + datum;
            }
            varConst = new VarConst(type, name, length++, data, h);
            int i = h & (cs.length - 1);
            while (cs[i] != null) {
                i = (i + 1) & (cs.length - 1);
            }
            cs[i] = varConst;
        }

        return varConst;
    }

    private void rehash() {
        if (cs == null) {
            cs = new VarConst[16];
        } else if (cs.length <= length << 1) {
            VarConst[] n = new VarConst[cs.length << 1];
            for (int i = 0; i < cs.length; i++) {
                VarConst vc = n[i];
                if (vc == null) {
                    continue;
                }

                i = vc.hash & (n.length - 1);
                while (n[i] != null) {
                    i = (i + 1) & (n.length - 1);
                }
                n[i] = vc;
            }
            cs = n;
        }
    }

    public VarConst get(CharSequence cs) {
        if (cs instanceof ReusedTmpCs) {
            ReusedTmpCs rtc = (ReusedTmpCs) cs;
            return get(rtc.arr, rtc.off, rtc.len);
        } else {
            byte[] data = cs.toString().getBytes();
            return get(data, 0, data.length);
        }
    }

    private VarConst get(byte[] arr, int off, int len) {
        int h = 1;
        for (int i = 0; i < len; i++) {
            int j;
            byte c;
            if ((c = arr[j = i + off]) == '-') {
                arr[j] = c = '_';
            } else if (c >= 'A' && c <= 'Z') {
                arr[j] = c |= 0x20;
            }
            h = h * 31 + c;
        }

        VarConst[] cs = this.cs;
        if (cs == null) {
            return null;
        }

        VarConst vc;
        int m;
        int i = h & (m = cs.length - 1);

        byte[] n;
        next:
        while ((vc = cs[i++ & m]) != null) {
            if (vc.hash != h || len != (n = vc.name).length) {
                continue;
            }
            for (int j = 0; j < len; j++) {
                if (n[j] != arr[j + off]) {
                    continue next;
                }
            }
            return vc;
        }
        return null;
    }

    public int getLength() {
        return length;
    }

    public void clear() {
        Arrays.fill(cs, null);
        length = 0;
    }

    public void forEach(Consumer<VarConst> consumer) {
        for (VarConst c : cs) {
            if (c != null) {
                consumer.accept(c);
            }
        }
    }

    public void fixBaseIdx(int baseIdx) {
        for (VarConst c : cs) {
            if (c != null) {
                c.setIdx(c.getIdx() + baseIdx);
            }
        }
    }
}
