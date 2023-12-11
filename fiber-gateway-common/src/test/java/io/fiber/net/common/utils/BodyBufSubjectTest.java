package io.fiber.net.common.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;

import static org.junit.Assert.*;

public class BodyBufSubjectTest {

    @Test
    public void t1() {
        EventLoopGroup bossGroup = EpollAvailable.bossGroup();
        bossGroup.submit(() -> {
            BodyBufSubject subject = new BodyBufSubject();
            for (int i = 0; i < 100; i++) {
                ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
                buffer.writeInt(32323);
                buffer.writeInt(32323);
                buffer.writeInt(32323);
                buffer.writeInt(32323);
                buffer.writeInt(32323);
                subject.onNext(buffer);
            }
            System.out.println("ssss");
        }).syncUninterruptibly();
        bossGroup.shutdownGracefully();
    }

}