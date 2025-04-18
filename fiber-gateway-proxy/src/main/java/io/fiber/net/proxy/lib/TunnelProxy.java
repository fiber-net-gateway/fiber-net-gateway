package io.fiber.net.proxy.lib;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.codec.ChannelUpgradeConnection;
import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.http.util.ConnectionFactory;
import io.fiber.net.http.util.IpUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.server.HttpExchange;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

public class TunnelProxy implements HttpDynamicFunc {
    private final ConnectionFactory connectionFactory;
    private final HttpClient httpClient;

    public TunnelProxy(ConnectionFactory connectionFactory, HttpClient httpClient) {
        this.connectionFactory = connectionFactory;
        this.httpClient = httpClient;
    }

    @Override
    public void call(ExecutionContext context) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);

        String host;
        int port;
        String schema;
        URI uri;
        try {
            uri = URI.create(exchange.getUri());
            host = uri.getHost();
            port = uri.getPort();
            schema = uri.getScheme();
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
        } catch (RuntimeException e) {
            context.throwErr(new ScriptExecException("tunnel proxy uri must be CONNECT <ip:port> : " + exchange.getUri()));
            return;
        }
        if (!exchange.getUri().startsWith("http://")) {
            port = -1;
            int i = exchange.getUri().indexOf(':');
            if (i == -1 || i >= exchange.getUri().length() - 1) {
                host = exchange.getUri();
            } else {
                host = exchange.getUri().substring(0, i);
                try {
                    port = Integer.parseInt(exchange.getUri().substring(i + 1));
                } catch (RuntimeException ignore) {
                }
            }
        }

        if (port <= 0 || port >= 65535 || StringUtils.isEmpty(host)) {
            context.throwErr(new ScriptExecException("tunnel proxy uri must be CONNECT <ip:port> : " + exchange.getUri()));
            return;
        }
        int timeout = 30000;

        if (HttpMethod.CONNECT == exchange.getRequestMethod()) {
            tunnelProxy(context, host, port, timeout, exchange);
        } else {
            if (StringUtils.isEmpty(schema)) {
                schema = HttpHost.DEFAULT_SCHEME_NAME;
            }
            HttpHost httpHost = new HttpHost(host, port, schema);
            ClientExchange clientExchange = httpClient.refer(httpHost);
            clientExchange.setRequestTimeout(timeout);
            httpProxy(uri, exchange, clientExchange, context);
        }

    }

    private void tunnelProxy(ExecutionContext context, String host, int port, int timeout, HttpExchange exchange) {
        SocketAddress sa;
        InetAddress address = IpUtils.tryToInetAddress(host);
        if (address == null) {
            sa = InetSocketAddress.createUnresolved(host, port);
        } else {
            sa = new InetSocketAddress(address, port);
        }
        if (!context.noArgs()) {
            int a = context.getArgVal(0).asInt(-1);
            if (a > 0) {
                timeout = a;
            }
        }
        EventLoop eventLoop = Scheduler.current().eventLoop();
        if (eventLoop == null) {
            context.throwErr(new ScriptExecException("tunnel proxy uri must be IO Thread"));
            return;
        }

        connectionFactory.connect(sa,
                3000,
                eventLoop,
                new Cb(context, timeout, exchange)
        );
    }

    private void httpProxy(URI uri, HttpExchange exchange, ClientExchange clientExchange, ExecutionContext context) {
        String rawPath = uri.getRawPath();
        if (StringUtils.isEmpty(rawPath)) {
            rawPath = "/";
        }
        if (StringUtils.isNotEmpty(uri.getRawQuery())) {
            clientExchange.setUri(rawPath + "?" + uri.getRawQuery());
        } else {
            clientExchange.setUri(rawPath);
        }
        for (String name : exchange.getRequestHeaderNames()) {
            clientExchange.addHeader(name, exchange.getRequestHeaderList(name));
        }
        String connection = exchange.getRequestHeader("Connection");

        if ("upgrade".equalsIgnoreCase(connection)) {
            clientExchange.setUpgradeAllowed(true);
            clientExchange.setHeaderUnsafe("Connection", connection);
            clientExchange.setHeaderUnsafe("Upgrade", exchange.getRequestHeader("Upgrade"));
        }

        clientExchange.setMethod(exchange.getRequestMethod());
        clientExchange.setReqBodyFunc(e -> exchange.readBodyUnsafe(), false);
        clientExchange.sendForResp()
                .subscribe((r, e) -> {
                    if (e != null) {
                        context.throwErr(new ScriptExecException(e.getMessage(), e));
                    } else {
                        HttpFunc.copyResponse(r, exchange, null, 60000, true);
                        context.returnVal(IntNode.valueOf(r.status()));
                    }
                });
    }

    private static class Cb implements ConnectionFactory.ConnCallback {
        private final ExecutionContext context;
        private final int timeout;
        private final HttpExchange exchange;

        private Cb(ExecutionContext context, int timeout, HttpExchange exchange) {
            this.context = context;
            this.timeout = timeout;
            this.exchange = exchange;
        }

        @Override
        public void onChannelCreated(Channel channel, EventLoop loop) throws Throwable {
            assert Scheduler.current().eventLoop() == loop;
        }

        @Override
        public void onConnectSuccess(Channel channel) {
            UpgradedConnection connection = new ChannelUpgradeConnection(channel, Scheduler.current(), timeout);
            UpgradedConnection downstream = exchange.upgrade(200, null, timeout);
            downstream.writeAndClose(connection.readDataUnsafe(), true);
            connection.writeAndClose(downstream.readDataUnsafe(), true);
            context.returnVal(NullNode.getInstance());
        }

        @Override
        public void onConnectError(Throwable e) {
            context.throwErr(ScriptExecException.fromThrowable(e));
        }
    }
}