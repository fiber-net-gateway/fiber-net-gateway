package io.fiber.net.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

public class TestServer {
    public static void main(String[] args) throws Throwable {

        ServerBootstrap sbt = new ServerBootstrap();
        sbt.channel(NioServerSocketChannel.class);
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        sbt.group(group, group);
        sbt.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new HttpServerCodec())
                        .addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof HttpRequest) {
                                    System.out.println("server received request: " + ((HttpRequest) msg).uri());
                                    HttpRequest req = (HttpRequest) msg;
                                    int s = req.headers().getInt("Content-Length", -1);
                                    if (s > 1024) {
                                        DefaultHttpHeaders headers = new DefaultHttpHeaders();
                                        headers.set("connection", "close");
                                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER, headers,
                                                EmptyHttpHeaders.INSTANCE);
                                        // write response ignore remaining data.
                                        ctx.writeAndFlush(response).addListener(f -> ctx.close());
                                    } else {
                                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER,
                                                EmptyHttpHeaders.INSTANCE,
                                                EmptyHttpHeaders.INSTANCE);
                                        ctx.writeAndFlush(response);
                                    }
                                }
                                super.channelRead(ctx, msg);
                            }
                        });

            }
        });
        sbt.localAddress(8080);
        ChannelFuture future = sbt.bind().syncUninterruptibly();
        future.channel().closeFuture().syncUninterruptibly();
    }


}
