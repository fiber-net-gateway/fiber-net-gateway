package io.fiber.net.http.util;

import io.fiber.net.http.impl.HttpConnectException;
import io.fiber.net.http.impl.HttpDnsException;
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

public class ConnectionFactory {
    private static class EpollSock {
        static Class<? extends DatagramChannel> datagramClass() {
            return EpollDatagramChannel.class;
        }

        static Class<? extends Channel> sockClass() {
            return EpollSocketChannel.class;
        }
    }

    public interface ConnCallback {
        void onChannelCreated(Channel channel, EventLoop loop) throws Throwable;

        void onConnectSuccess(Channel channel);

        void onConnectError(Throwable e);
    }

    private final ChannelConfig channelConfig;
    private final io.netty.channel.ChannelFactory<Channel> cf;
    private final EventLoopGroup group;
    private final AddressResolverGroup<InetSocketAddress> resolver;


    public EventLoopGroup getGroup() {
        return group;
    }

    public ConnectionFactory(EventLoopGroup group, ChannelConfig channelConfig) {
        this.group = group;
        this.channelConfig = channelConfig;

        DnsNameResolverBuilder dnsNameResolverBuilder = new DnsNameResolverBuilder();

        if ("EpollEventLoopGroup".equals(group.getClass().getSimpleName())) {
            dnsNameResolverBuilder.channelType(EpollSock.datagramClass());
            cf = new ReflectiveChannelFactory<>(EpollSock.sockClass());
        } else {
            dnsNameResolverBuilder.channelType(NioDatagramChannel.class);
            cf = new ReflectiveChannelFactory<>(NioSocketChannel.class);
        }
        if (channelConfig.enableDnsCache) {
            dnsNameResolverBuilder.resolveCache(new DefaultDnsCache());
        }

        if (channelConfig.enableDnsCnameCache) {
            dnsNameResolverBuilder.cnameCache(new DefaultDnsCnameCache());
        }
        dnsNameResolverBuilder.queryTimeoutMillis(channelConfig.dnsQueryTimeout);
        this.resolver = new DnsAddressResolverGroup(dnsNameResolverBuilder);
    }

    public void connect(SocketAddress address,
                        int timeout,
                        EventLoop eventLoop,
                        ConnCallback callback) {
        assert eventLoop.parent() == group;

        Channel channel = cf.newChannel();
        io.netty.channel.ChannelConfig config = channel.config();
        config.setOption(ChannelOption.SO_KEEPALIVE, channelConfig.tcpKeepalive);
        config.setOption(ChannelOption.TCP_NODELAY, channelConfig.tcpNoDelay);
        config.setOption(ChannelOption.SO_REUSEADDR, channelConfig.reuseAddr);
        if (timeout > 0) {
            config.setConnectTimeoutMillis(timeout);
        }

        try {
            callback.onChannelCreated(channel, eventLoop);
        } catch (Throwable err) {
            callback.onConnectError(err);
            return;
        }

        ChannelFuture f = eventLoop.register(channel);
        if (!f.isSuccess()) {
            // not hit
            callback.onConnectError(f.cause());
            return;
        }

        AddressResolver<InetSocketAddress> resolverResolver = resolver.getResolver(eventLoop);

        if (resolverResolver.isSupported(address) && !resolverResolver.isResolved(address)) {
            Promise<InetSocketAddress> socketAddressPromise = eventLoop.<InetSocketAddress>newPromise()
                    .addListener(future -> resolvedAndConnect(callback, channel, future));
            resolverResolver.resolve(address, socketAddressPromise);
        } else {
            connect0(callback, channel, address);
        }
    }

    private static void resolvedAndConnect(ConnCallback callback,
                                           Channel ch,
                                           Future<? super InetSocketAddress> resolved) {
        if (resolved.isSuccess()) {
            connect0(callback, ch, (SocketAddress) resolved.getNow());
        } else {
            callback.onConnectError(new HttpDnsException("dns resolve error", resolved.cause(), 503));
        }
    }

    private static void connect0(ConnCallback callback, Channel ch, SocketAddress address) {
        ch.connect(address, ch.newPromise().addListener(future -> {
            if (future.isSuccess()) {
                callback.onConnectSuccess(ch);
            } else {
                callback.onConnectError(new HttpConnectException("cannot connect to " + address, future.cause(), 502));
            }
        }));
    }
}
