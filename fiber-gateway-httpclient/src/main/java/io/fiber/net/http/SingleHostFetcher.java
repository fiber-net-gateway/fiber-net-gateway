package io.fiber.net.http;


import io.fiber.net.http.util.IpUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class SingleHostFetcher implements HostFetcher {

    private final HttpHost httpHost;
    private final String key;

    public SingleHostFetcher(HttpHost httpHost) {
        this.httpHost = httpHost;
        key = httpHost.getHostText();
    }

    public SingleHostFetcher(String host, int port) {
        InetAddress address = IpUtils.tryToInetAddress(host);
        if (address != null) {
            httpHost = new HttpHost(new InetSocketAddress(address, port), host, null);
        } else {
            httpHost = new HttpHost(host, port, null);
        }
        if (port > 0) {
            this.key = host + ":" + port;
        } else {
            this.key = host;
        }
    }

    @Override
    public void fetchHost(Acceptor acceptor) {
        acceptor.setHostKey(key, httpHost);
    }

    @Override
    public HttpHost getHttpHost() {
        return httpHost;
    }
}
