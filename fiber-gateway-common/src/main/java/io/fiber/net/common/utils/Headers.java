package io.fiber.net.common.utils;

public class Headers {

    public static boolean isHopHeaders(String key) {
        if (key == null) {
            return false;
        }
        switch (key) {
            case "Connection":
            case "Content-Length":
            case "Proxy-Connection": // non-standard but still sent by libcurl and rejected by e.g. google
            case "Keep-Alive":
            case "Proxy-Authenticate":
            case "Proxy-Authorization":
            case "Te":      // canonicalized version of "TE"
            case "Trailer": // not Trailers per URL above; http://www.rfc-editor.org/errata_search.php?eid=4522
            case "Transfer-Encoding":
            case "Upgrade":
                return true;
        }
        switch (key.toLowerCase()) {
            case "connection":
            case "content-length":
            case "proxy-connection":
            case "keep-alive":
            case "proxy-authenticate":
            case "proxy-authorization":
            case "te":
            case "trailer":
            case "transfer-encoding":
            case "upgrade":
                return true;
        }
        return false;
    }

    public static String getHeaderLowercase(String headerKey) {
        switch (headerKey.length()) {
            case 4:
                switch (headerKey) {
                    case "Host":
                        return "host";
                    case "X-Ua":
                        return "x-ua";
                }
                break;
            case 6:
                switch (headerKey) {
                    case "Cookie":
                        return "cookie";
                    case "Origin":
                        return "origin";
                    case "Expect":
                        return "expect";
                    case "Accept":
                        return "accept";
                }
                break;
            case 7:
                switch (headerKey) {
                    case "Referer":
                        return "referer";
                    case "X-Shard":
                        return "x-shard";
                    case "X-Sinfo":
                        return "x-sinfo";
                }
                break;
            case 10:
                switch (headerKey) {
                    case "Set-Cookie":
                        return "set-cookie";
                    case "User-Agent":
                        return "user-agent";
                    case "Connection":
                        return "connection";
                }
                break;
            case 12:
                switch (headerKey) {
                    case "Content-Type":
                        return "content-type";
                    case "X-Sopush-Ttl":
                        return "x-sopush-ttl";
                    case "Ns-Client-Ip":
                        return "ns-client-ip";
                }
                break;
            case 13:
                switch (headerKey) {
                    case "Cache-Control":
                        return "cache-control";
                    case "If-None-Match":
                        return "if-none-match";
                    case "X-Eleme-Rpcid":
                        return "x-eleme-rpcid";
                    case "X-Real-Scheme":
                        return "x-real-scheme";
                }
                break;
            case 14:
                switch (headerKey) {
                    case "Content-Length":
                        return "content-length";
                    case "Eagleeye-Rpcid":
                        return "eagleeye-rpcid";
                    case "X-Client-Http2":
                        return "x-client-http2";
                    case "Accept-Charset":
                        return "accept-charset";
                    case "Ns-Client-Port":
                        return "ns-client-port";
                    case "X-Pizza-Router":
                        return "x-pizza-router";
                }
                break;
            case 15:
                switch (headerKey) {
                    case "Accept-Encoding":
                        return "accept-encoding";
                    case "Accept-Language":
                        return "accept-language";
                    case "X-Forwarded-For":
                        return "x-forwarded-for";
                    case "X-Client-Scheme":
                        return "x-client-scheme";
                    case "Proxy-Client-Ip":
                        return "proxy-client-ip";
                    case "Web-Server-Type":
                        return "web-server-type";
                }
                break;
            case 16:
                switch (headerKey) {
                    case "X-Forwarded-Host":
                        return "x-forwarded-host";
                    case "X-Forwarded-Port":
                        return "x-forwarded-port";
                    case "Eagleeye-Traceid":
                        return "eagleeye-traceid";
                }
                break;
            case 17:
                switch (headerKey) {
                    case "If-Modified-Since":
                        return "if-modified-since";
                    case "X-Eleme-Requestid":
                        return "x-eleme-requestid";
                    case "X-Forwarded-Proto":
                        return "x-forwarded-proto";
                }
                break;
            case 11:
                switch (headerKey) {
                    case "X-Eleme-Seq":
                        return "x-eleme-seq";
                }
                break;
            case 8:
                switch (headerKey) {
                    case "X-Router":
                        return "x-router";
                }
                break;
            case 20:
                switch (headerKey) {
                    case "X-Origin-Request-Uri":
                        return "x-origin-request-uri";
                }
                break;
            case 22:
                switch (headerKey) {
                    case "X-Sopush-Original-Host":
                        return "x-sopush-original-host";
                }
                break;
            case 18:
                switch (headerKey) {
                    case "Wl-Proxy-Client-Ip":
                        return "wl-proxy-client-ip";
                }
                break;
            case 9:
                switch (headerKey) {
                    case "X-Real-Ip":
                        return "x-real-ip";
                }
        }
        return null;
    }

    /**
     * 把常用的header key 存到常量池
     *
     * @param headerKey headerKey
     * @return headerKey.toLowerCase()
     */
    public static String toLowerCase(String headerKey) {
        String host = getHeaderLowercase(headerKey);
        if (host != null) return host;
        return headerKey.toLowerCase();
    }
}
