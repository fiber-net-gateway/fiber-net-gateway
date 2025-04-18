package io.fiber.net.common.codec;

import io.fiber.net.common.async.Observable;
import io.netty.buffer.*;
import io.netty.util.ReferenceCounted;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPOutputStream;

public class BufCodecTest {

    @Test
    public void decode() throws Exception {

        byte[] raw = new byte[1024 * 1024];

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) random.nextInt(-128, 128);
        }

        ByteArrayOutputStream gziped = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(gziped);
        stream.write(raw);
        stream.close();

        byte[] bytes = gziped.toByteArray();
        Observable<ByteBuf> observable = Observable.create(emitter -> {
            if (emitter.isDisposed()) {
                return;
            }
            int i = 0;
            while (i < bytes.length) {
                int s = random.nextInt(128);
                s = Math.min(bytes.length - i, s);
                emitter.onNext(Unpooled.wrappedBuffer(bytes, i, s));
                i += s;
                System.out.println("发送：" + i);
            }
            emitter.onComplete();
        });

        Observable<ByteBuf> rawOb = BufCodec.decode(observable, "gzip");
        rawOb.toMaybe((a, b) -> {
            if (a instanceof CompositeByteBuf) {
                ((CompositeByteBuf) a).addFlattenedComponents(true, b);
                return a;
            }
            CompositeByteBuf bufs = ByteBufAllocator.DEFAULT.compositeBuffer();
            bufs.addFlattenedComponents(true, a);
            bufs.addFlattenedComponents(true, b);
            return bufs;
        }, ReferenceCounted::release)
                .subscribe((r, e) -> {
                    if (e != null) {
                        e.printStackTrace();
                    }

                    if (r != null) {
                        byte[] pp = ByteBufUtil.getBytes(r);
                        Assert.assertArrayEquals(raw, pp);
                        r.release();
                        System.out.println("成功。。。。。");
                    } else {
                        Assert.fail();
                    }
                });

    }
}
