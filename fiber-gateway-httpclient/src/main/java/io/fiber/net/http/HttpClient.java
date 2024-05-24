package io.fiber.net.http;

public interface HttpClient {

    ClientExchange refer(String host, int port);
    ClientExchange refer(HttpHost httpHost);

}
