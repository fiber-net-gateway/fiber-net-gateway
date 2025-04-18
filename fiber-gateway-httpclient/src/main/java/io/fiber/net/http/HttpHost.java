package io.fiber.net.http;

import io.fiber.net.common.utils.Predictions;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.util.IpUtils;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.Objects;

public class HttpHost implements Cloneable, Serializable {

    private static final long serialVersionUID = -7529410654042457626L;
    private static final int HASH_SEED = 17;
    private static final int HASH_OFFSET = 37;
    public static final String DEFAULT_SCHEME_NAME = "http";
    public static final String SECURE_SCHEME_NAME = "https";

    private final SocketAddress address;
    private final String schemeName;
    private final String hostname;
    private final int port;
    private final int realPort;
    private final String hostText;

    private final int hash;

    public HttpHost(final String hostname, int port, String scheme) {
        this.hostname = Predictions.assertTextNotEmpty(hostname, "hostname must be textual").toLowerCase();
        scheme = internalSchema(scheme, port);
        this.schemeName = scheme;
        this.port = port;
        if (port <= 0) {
            realPort = scheme.equals(SECURE_SCHEME_NAME) ? 443 : 80;
        } else {
            realPort = port;
        }
        this.hostText = realPort == defaultPort(scheme) ? hostname : hostname + ":" + realPort;
        InetAddress inetAddress;
        this.address = (inetAddress = IpUtils.tryToInetAddress(hostname)) != null ? new InetSocketAddress(inetAddress, realPort)
                : InetSocketAddress.createUnresolved(hostname, realPort);
        this.hash = getHash();
    }

    private static String internalSchema(String scheme, int port) {
        if (StringUtils.isEmpty(scheme)) {
            scheme = port == 443 ? SECURE_SCHEME_NAME : DEFAULT_SCHEME_NAME;
        } else {
            scheme = SECURE_SCHEME_NAME.equalsIgnoreCase(scheme) ? SECURE_SCHEME_NAME : DEFAULT_SCHEME_NAME;
        }
        return scheme;
    }

    private static int defaultPort(String scheme) {
        return SECURE_SCHEME_NAME.equals(scheme) ? 443 : 80;
    }

    private int getHash() {
        int hash = HASH_SEED;
        hash = hashCode(hash, this.hostname);
        hash = hashCode(hash, realPort);
        hash = hashCode(hash, this.schemeName);
        return hash;
    }

    public HttpHost(final String hostname, final int port) {
        this(hostname, port, null);
    }

    public static HttpHost create(final String s) {
        Predictions.textNotEmpty(s, "HTTP Host is null");
        String text = s;
        String scheme = null;
        final int schemeIdx = text.indexOf("://");
        if (schemeIdx > 0) {
            scheme = text.substring(0, schemeIdx);
            text = text.substring(schemeIdx + 3);
        }
        int port = -1;
        final int portIdx = text.lastIndexOf(":");
        if (portIdx > 0) {
            try {
                port = Integer.parseInt(text.substring(portIdx + 1));
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid HTTP host: " + text);
            }
            text = text.substring(0, portIdx);
        }
        return new HttpHost(text, port, scheme);
    }

    public HttpHost(final String hostname) {
        this(hostname, -1, null);
    }

    public HttpHost(final InetSocketAddress address, final String scheme) {
        this(Objects.requireNonNull(address, "Inet address"), address.getHostName(), scheme);
    }

    public HttpHost(final InetSocketAddress address, final String hostname, String scheme) {
        this.address = Objects.requireNonNull(address);
        this.hostname = hostname.toLowerCase(Locale.ROOT);
        this.port = address.getPort();
        this.realPort = port;
        this.schemeName = scheme = internalSchema(scheme, port);
        this.hostText = realPort == defaultPort(scheme) ? hostname : hostname + ":" + realPort;
        this.hash = getHash();
    }


    /**
     * Returns the host name.
     *
     * @return the host name (IP or DNS name)
     */
    public String getHostName() {
        return this.hostname;
    }

    public int getRealPort() {
        return realPort;
    }

    /**
     * Returns the port.
     *
     * @return the host port, or {@code -1} if not set
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Returns the scheme name.
     *
     * @return the scheme name
     */
    public String getSchemeName() {
        return this.schemeName;
    }

    /**
     * Returns the inet address if explicitly set by a constructor,
     * {@code null} otherwise.
     *
     * @return the inet address
     * @since 4.3
     */
    public SocketAddress getAddress() {
        return this.address;
    }

    /**
     * Return the host URI, as a string.
     *
     * @return the host URI
     */
    public String toURI() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.schemeName);
        buffer.append("://");
        buffer.append(this.hostname);
        if (this.port != -1) {
            buffer.append(':');
            buffer.append(this.port);
        }
        return buffer.toString();
    }

    /**
     * for Host header
     *
     * @return host
     */
    public String getHostText() {
        return hostText;
    }

    @Override
    public String toString() {
        return toURI();
    }


    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HttpHost) {
            final HttpHost that = (HttpHost) obj;
            return this.hostname.equals(that.hostname)
                    && this.realPort == that.realPort
                    && this.schemeName.equals(that.schemeName);
        }
        return false;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public final int hashCode() {
        return hash;
    }


    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    private static int hashCode(final int seed, final int hashcode) {
        return seed * HASH_OFFSET + hashcode;
    }

    private static int hashCode(final int seed, final Object obj) {
        return hashCode(seed, obj != null ? obj.hashCode() : 0);
    }

    public boolean isSecure() {
        return "https".equals(schemeName);
    }

    public static HttpHost create(String host, int port) {
        InetAddress address = IpUtils.tryToInetAddress(host);
        if (address != null) {
            if (port <= 0) {
                port = 80;
            }
            return new HttpHost(new InetSocketAddress(address, port), host, null);
        } else {
            return new HttpHost(host, port, null);
        }
    }
}