package io.fiber.net.http;

public interface HostFetcher {

    interface Acceptor {
        /**
         * @param hostKey key for monitor name nonnull
         * @param host    host for ip
         */
        void setHostKey(String hostKey, HttpHost host);
    }

    void fetchHost(Acceptor acceptor);

    HttpHost getHttpHost();
}
