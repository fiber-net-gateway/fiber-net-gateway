package io.fiber.net.proxy;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.Script;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class RoutePathMatcherTest {

    RoutePathMatcher.Builder<TS> builder;
    RoutePathMatcher<TS> matcher;

    @Test
    public void matchPath() {
        builder = RoutePathMatcher.builder();
        TS ts0 = new TS(HttpMethod.GET, "/a/b/c");
        TS ts1 = new TS(HttpMethod.GET, "/c/v/c");
        TS ts2 = new TS(HttpMethod.GET, "/a/b/e");
        TS ts3 = new TS(HttpMethod.GET, "/aa/b/f");
        TS ts4 = new TS(HttpMethod.GET, "/a/b/g");
        TS ts5 = new TS(HttpMethod.GET, "/a/b/g/");
        TS ts6 = new TS(HttpMethod.GET, "/a/b/a/a");
        TS ts7 = new TS(null, "/a/b/a/a");

        TS ts8 = new TS(HttpMethod.GET, "/aa/b/:f");
        TS ts9 = new TS(HttpMethod.GET, "/aa/b/*f");
        TS ts10 = new TS(HttpMethod.GET, "/aa/b/:ff/vv");
        TS ts11 = new TS(null, "/aa/b/:ff/kk/");
        TS ts12 = new TS(HttpMethod.GET, "/aa/b/:ff/vv/:aa/3");

        add(ts0, ts1, ts2, ts3, ts4, ts5, ts6, ts7, ts8, ts9, ts10, ts11, ts12);
        matcher = builder.build();

        match(new E(HttpMethod.GET, "/a/b/g"), ts4);
        match(new E(HttpMethod.GET, "/a/b/a/a"), ts6);
        match(new E(HttpMethod.GET, "/a/b/c"), ts0);
        match(new E(HttpMethod.GET, "/a/b/g/"), ts5);
        match(new E(HttpMethod.GET, "/a/b/g"), ts4);
        match(new E(HttpMethod.DELETE, "/a/b/a/a"), ts7);
        match(new E(HttpMethod.GET, "/a/b/a/a"), ts6);

        Assert.assertNull(matcher.matchPath(new E(HttpMethod.POST, "/a/b/g")).getHandler());
        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.GET, "/aa/b/tt"));
            Assert.assertSame(result.getHandler(), ts8);
            Assert.assertEquals(result.getMap().size(), 1);
            Assert.assertEquals(TextNode.valueOf("tt"), result.getMap().get("f"));
        }

        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.GET, "/aa/b/tt/vv"));
            Assert.assertSame(result.getHandler(), ts10);
            Assert.assertEquals(result.getMap().size(), 1);
            Assert.assertEquals(TextNode.valueOf("tt"), result.getMap().get("ff"));
        }
        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.PUT, "/aa/b/tt/kk/"));
            Assert.assertSame(result.getHandler(), ts11);
            Assert.assertEquals(result.getMap().size(), 1);
            Assert.assertEquals(TextNode.valueOf("tt"), result.getMap().get("ff"));
        }

        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.GET, "/aa/b/tt/vv/bb/3"));
            Assert.assertSame(result.getHandler(), ts12);
            Assert.assertEquals(result.getMap().size(), 2);
            Assert.assertEquals(TextNode.valueOf("tt"), result.getMap().get("ff"));
            Assert.assertEquals(TextNode.valueOf("bb"), result.getMap().get("aa"));
        }

        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.GET, "/aa/b/tt/vvt"));
            Assert.assertSame(result.getHandler(), ts9);
            Assert.assertEquals(result.getMap().size(), 1);
            Assert.assertEquals(TextNode.valueOf("tt/vvt"), result.getMap().get("f"));
        }

        {
            RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(new E(HttpMethod.GET, "/aa/b/tt/"));
            Assert.assertSame(result.getHandler(), ts9);
            Assert.assertEquals(result.getMap().size(), 1);
            Assert.assertEquals(TextNode.valueOf("tt/"), result.getMap().get("f"));
        }

        {
            Assert.assertNull(matcher.matchPath(new E(HttpMethod.POST, "/aa/b/tt")).getHandler());
        }

    }

    private void match(E e, TS ts) {
        RoutePathMatcher.MappingResult<TS> result = matcher.matchPath(e);
        Assert.assertSame(ts, result.getHandler());
        Assert.assertTrue(result.getMap().isEmpty());
    }

    private void add(TS... tss) {
        for (TS ts : tss) {
            builder.addUrlHandler(ts.method, ts.url, ts);
        }
    }


    private static class TS implements Script {
        private final String url;
        private final HttpMethod method;

        private TS(HttpMethod method, String url) {
            this.url = url;
            this.method = method;
        }

        @Override
        public Maybe<JsonNode> exec(JsonNode root, Object attach) {
            return null;
        }

        @Override
        public Maybe<JsonNode> aotExec(JsonNode root, Object attach) throws Exception {
            return null;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            TS ts = (TS) object;
            return Objects.equals(url, ts.url) && method == ts.method;
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, method);
        }

        @Override
        public String toString() {
            return "TS{" +
                    "url='" + url + '\'' +
                    ", method=" + method +
                    '}';
        }
    }

    private static class E extends HttpExchange {
        private final HttpMethod method;
        private final String path;

        private E(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getQuery() {
            return null;
        }

        @Override
        public String getUri() {
            return null;
        }

        @Override
        public void setRequestHeader(String name, String value) {

        }

        @Override
        public void addRequestHeader(String name, String value) {

        }

        @Override
        public String getRequestHeader(String name) {
            return null;
        }

        @Override
        public List<String> getRequestHeaderList(String name) {
            return null;
        }

        @Override
        public Collection<String> getRequestHeaderNames() {
            return null;
        }

        @Override
        public void setResponseHeader(String name, String value) {

        }

        @Override
        public void addResponseHeader(String name, String value) {

        }

        @Override
        public void removeResponseHeader(String name) {

        }

        @Override
        public String getResponseHeader(String name) {
            return null;
        }

        @Override
        public List<String> getResponseHeaderList(String name) {
            return null;
        }

        @Override
        public Collection<String> getResponseHeaderNames() {
            return null;
        }

        @Override
        public HttpMethod getRequestMethod() {
            return method;
        }

        @Override
        public void writeJson(int status, Object result) throws FiberException {

        }

        @Override
        public void writeRawBytes(int status, ByteBuf buf) throws FiberException {

        }

        @Override
        public void writeRawBytes(int status, Observable<ByteBuf> bufOb, boolean flush) throws FiberException {

        }

        @Override
        public boolean isResponseWrote() {
            return false;
        }

        @Override
        public void discardReqBody() {

        }

        @Override
        public Observable<ByteBuf> readBodyUnsafe() {
            return null;
        }

        @Override
        public Maybe<ByteBuf> readFullBody(Scheduler scheduler) {
            return null;
        }
    }

}