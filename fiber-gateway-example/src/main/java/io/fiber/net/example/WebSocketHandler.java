package io.fiber.net.example;

import io.fiber.net.common.codec.UpgradedConnection;
import io.fiber.net.common.ext.RouterHandler;
import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.server.HttpExchange;

public class WebSocketHandler implements RouterHandler<HttpExchange> {

    private HttpClient httpClient;

    public WebSocketHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getRouterName() {
        return "websocket";
    }

    @Override
    public void invoke(HttpExchange exchange) {
        ClientExchange clientExchange = httpClient.refer(HttpHost.create("http://localhost:8080"));
        for (String name : exchange.getRequestHeaderNames()) {
            clientExchange.setHeader(name, exchange.getRequestHeaderList(name));
        }
        clientExchange.setUri(exchange.getUri());
        clientExchange.setReqBodyFunc(r -> exchange.readBodyUnsafe(), false);

        if ("upgrade".equalsIgnoreCase(exchange.getRequestHeader("Connection"))) {
            clientExchange.setUpgradeAllowed(true);
            clientExchange.setHeaderUnsafe("Upgrade", "websocket");
            clientExchange.setHeaderUnsafe("Connection", "Upgrade");
        }
        clientExchange.sendForResp().subscribe((r, e) -> {
            if (e != null) {
                ErrorInfo info = ErrorInfo.of(e);
                exchange.writeJson(info.getStatus(), info);
                return;
            }
            for (String name : r.getHeaderNames()) {
                exchange.setResponseHeader(name, r.getHeaderList(name));
            }

            if (r.isUpgraded()) {
                String upgrade = r.getHeader("Upgrade");
                UpgradedConnection downstream = exchange.upgrade(r.status(), upgrade, 30000);
                UpgradedConnection upstream = r.upgradeConnection();
                downstream.writeAndClose(upstream.readDataUnsafe(), true);
                upstream.writeAndClose(downstream.readDataUnsafe(), true);
            } else {
                exchange.writeRawBytes(r.status(), r.readRespBodyUnsafe());
            }
        });
    }

    @Override
    public void destroy() {

    }
}