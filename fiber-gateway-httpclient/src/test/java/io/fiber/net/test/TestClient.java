package io.fiber.net.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class TestClient {
    public static void main(String[] args) throws Throwable {
        Bootstrap b = new Bootstrap();
        // set auto_read is false
        b.option(ChannelOption.AUTO_READ, false);
        b.group(new NioEventLoopGroup(1));
        b.channel(NioSocketChannel.class);
        CountDownLatch latch = new CountDownLatch(1);
        b.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                channel.pipeline()
                        .addLast(new ChannelDuplexHandler() {
                            int writeCount = 0;

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof ByteBuf) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    System.out.println("response: " + buf.readCharSequence(buf.readableBytes(), StandardCharsets.ISO_8859_1));
                                    latch.countDown();
                                }
                                super.channelRead(ctx, msg);
                            }

                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                writeCount++;
                                if (writeCount == 2) {
                                    // AUTO_READ is false. read response after body sent.
                                    promise = promise.unvoid().addListener(f -> {
                                        ctx.read();
                                    });
                                }
                                ctx.write(msg, promise);
                            }
                        });
            }
        });
        ChannelFuture future = b.connect("127.0.0.1", 8080)
                .syncUninterruptibly();
        if (!future.isSuccess()) {
            throw future.cause();
        }

        // send header. the length is 1025. It is a big request
        ByteBuf reqHeader = ByteBufAllocator.DEFAULT.buffer(200);
        reqHeader.writeCharSequence("GET /index.html HTTP/1.1\r\n", StandardCharsets.ISO_8859_1);
        reqHeader.writeCharSequence("Content-Length: 1025\r\n\r\n", StandardCharsets.ISO_8859_1);
        future.channel().writeAndFlush(reqHeader);

        //
        Thread.sleep(1000);

        // send body
        ByteBuf reqBody = ByteBufAllocator.DEFAULT.buffer(1025);
        reqBody.writeZero(1025);
        future.channel().writeAndFlush(reqBody);

        // forever wait response if AUTO_READ is true .... cannot receive response from the channel
        latch.await();
        future.channel().close().syncUninterruptibly();
        b.config().group().shutdownGracefully().syncUninterruptibly();
    }
}
