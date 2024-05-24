package io.fiber.net.http;


public class SingleHostFetcher implements HostFetcher {

    private final HttpHost httpHost;
    private final String key;

    public SingleHostFetcher(HttpHost httpHost) {
        this.httpHost = httpHost;
        key = httpHost.getHostText();
    }

    public SingleHostFetcher(String host, int port) {
        httpHost = HttpHost.create(host, port);
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
