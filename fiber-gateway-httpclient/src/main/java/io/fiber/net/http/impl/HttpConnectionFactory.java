package io.fiber.net.http.impl;

import io.fiber.net.http.HttpHost;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DefaultDnsCnameCache;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class HttpConnectionFactory {

    private static class EpollSock {
        static Class<? extends DatagramChannel> datagramClass() {
            return EpollDatagramChannel.class;
        }

        static Class<? extends Channel> sockClass() {
            return EpollSocketChannel.class;
        }
    }

    private final ChannelFactory<Channel> cf;
    private final EventLoopGroup group;
    private final AddressResolverGroup<InetSocketAddress> resolver;
    private final PoolConfig poolConfig;

    public HttpConnectionFactory(EventLoopGroup group, PoolConfig poolConfig) {
        this.group = group;
        this.poolConfig = poolConfig;

        DnsNameResolverBuilder dnsNameResolverBuilder = new DnsNameResolverBuilder();

        if ("EpollEventLoopGroup".equals(group.getClass().getSimpleName())) {
            dnsNameResolverBuilder.channelType(EpollSock.datagramClass());
            cf = new ReflectiveChannelFactory<>(EpollSock.sockClass());
        } else {
            dnsNameResolverBuilder.channelType(NioDatagramChannel.class);
            cf = new ReflectiveChannelFactory<>(NioSocketChannel.class);
        }
        if (poolConfig.enableDnsCache) {
            dnsNameResolverBuilder.resolveCache(new DefaultDnsCache());
        }

        if (poolConfig.enableDnsCnameCache) {
            dnsNameResolverBuilder.cnameCache(new DefaultDnsCnameCache());
        }
        dnsNameResolverBuilder.queryTimeoutMillis(5000);
        this.resolver = new DnsAddressResolverGroup(dnsNameResolverBuilder);
    }

    public void connect(HttpHost httpHost, int timeout, EventLoop eventLoop, Promise<HttpConnection> result) {
        assert eventLoop.parent() == group;

        Channel channel = cf.newChannel();
        ChannelConfig config = channel.config();
        config.setOption(ChannelOption.SO_KEEPALIVE, poolConfig.tcpKeepalive);
        config.setOption(ChannelOption.TCP_NODELAY, poolConfig.tcpNoDelay);
        config.setOption(ChannelOption.SO_REUSEADDR, poolConfig.reuseAddr);
        if (timeout > 0) {
            config.setConnectTimeoutMillis(timeout);
        }

        HttpConnectionHandler handler = new HttpConnectionHandler(channel, httpHost, eventLoop, poolConfig);
        channel.pipeline().addLast(handler);
        ChannelFuture f = eventLoop.register(channel);
        if (!f.isSuccess()) {
            // not hit
            result.setFailure(f.cause());
            return;
        }

        AddressResolver<InetSocketAddress> resolverResolver = resolver.getResolver(eventLoop);
        SocketAddress address = httpHost.getAddress();

        if (resolverResolver.isSupported(address) && !resolverResolver.isResolved(address)) {
            Promise<InetSocketAddress> socketAddressPromise = eventLoop.<InetSocketAddress>newPromise()
                    .addListener(future -> resolvedAndConnect(result, handler, future));
            resolverResolver.resolve(address, socketAddressPromise);
        } else {
            connect0(result, handler, address);
        }
    }

    private static void resolvedAndConnect(Promise<HttpConnection> result,
                                           HttpConnectionHandler handler,
                                           Future<? super InetSocketAddress> resolved) {
        if (resolved.isSuccess()) {
            connect0(result, handler, (SocketAddress) resolved.getNow());
        } else {
            result.setFailure(new HttpDnsException("dns resolve error", resolved.cause(), 503));
        }
    }

    private static void connect0(Promise<HttpConnection> result, HttpConnectionHandler handler, SocketAddress address) {
        handler.getCh().connect(address, handler.getCh().newPromise().addListener(future -> {
            if (future.isSuccess()) {
                result.setSuccess(handler);
            } else {
                result.setFailure(new HttpConnectException("cannot connect to " + address, future.cause(), 502));
            }
        }));
    }

}
