package io.fiber.net.proxy;

import io.fiber.net.proxy.route.RouteConflictException;
import io.fiber.net.proxy.route.RoutePathMatcher;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class RoutePathMatcherTest {

    private static class TestVarDefiner implements RoutePathMatcher.RouteVarDefiner<Object, Object> {
        Set<Integer> set = new HashSet<>();

        @Override
        public void addPathVarDefiner(Object builder, String varName, int idx) {
            System.out.println("addPathVarDefiner: " + builder + " -> " + varName + " " + idx);
        }

        @Override
        public Object onRouteMount(int routeNodeId, String fullPath, Object builder) throws RouteConflictException {
            Assert.assertTrue(set.add(routeNodeId));
            return builder;
        }
    }

    private static class Tester implements RoutePathMatcher.MappingContext<Object> {
        RoutePathMatcher.Builder<Object, Object> builder = RoutePathMatcher.builder(new TestVarDefiner());
        private int id = 0;
        private RoutePathMatcher<Object> matcher;
        private Object matchedToken;
        private final ArrayList<String> pathVars = new ArrayList<>();

        Object addPath(String pathPattern) {
            Object r = id++;
            builder.addUrlHandler(pathPattern, r);
            return r;
        }

        void test(String path, Object token, Map<String, String> values) {
            if (values == null) {
                values = Collections.emptyMap();
            }

            values = new HashMap<>(values);
            this.matchedToken = token;
            if (matcher == null) {
                matcher = builder.build();
            }
            Assert.assertTrue(matcher.matchPath(path, this));
            for (int i = 0; i < pathVars.size(); i += 2) {
                Assert.assertTrue(values.remove(pathVars.get(i), pathVars.get(i + 1)));
            }
            Assert.assertTrue(values.isEmpty());
            pathVars.clear();
        }

        void test(String path, Object token) {
            test(path, token, Collections.emptyMap());
        }

        void testUnmatched(String path) {
            if (matcher == null) {
                matcher = builder.build();
            }
            Assert.assertFalse(matcher.matchPath(path, this));
        }


        @Override
        public boolean matched(int nodeId, Object handler) {
            Assert.assertSame(matchedToken, handler);
            return true;
        }

        @Override
        public void addPathVar(String var, String value) {
            pathVars.add(var);
            pathVars.add(value);
        }

        @Override
        public void popPathVar() {
            pathVars.remove(pathVars.size() - 1);
            pathVars.remove(pathVars.size() - 1);
        }
    }


    @Test
    public void builder1() {
        {
            Tester tester = new Tester();
            Object i1 = tester.addPath("/a/b/");
            Object i2 = tester.addPath("/a/b/*tail");
            Object i0 = tester.addPath("/a/b");
            tester.test("/a/b", i0);
            tester.test("/a/b/", i1);
            tester.test("/a/b/aa", i2, Collections.singletonMap("tail", "aa"));
        }
        {
            Tester tester = new Tester();
            Object i1 = tester.addPath("/a/b/");
            Object i2 = tester.addPath("/a/b/*tail");
            tester.test("/a/b", i1);
            tester.test("/a/b/", i1);
            tester.test("/a/b/aa", i2, Collections.singletonMap("tail", "aa"));
            tester.test("/a/b/aa/bb", i2, Collections.singletonMap("tail", "aa/bb"));
            tester.test("/a/b/aa/bb/cc.ccc", i2, Collections.singletonMap("tail", "aa/bb/cc.ccc"));
        }

    }


    @Test
    public void builder2() {
        {
            Tester tester = new Tester();
            Object i1 = tester.addPath("/a/b/");
            Object i2 = tester.addPath("/a/b/*tail");
            Object i3 = tester.addPath("/a/b/:ph");
            Object i0 = tester.addPath("/a/b");
            tester.test("/a/b", i0);
            tester.test("/a/b/", i1);
            tester.test("/a/b/aa", i3, Collections.singletonMap("ph", "aa"));
            tester.test("/a/b/aa/", i3, Collections.singletonMap("ph", "aa"));
            tester.test("/a/b/aa/xx", i2, Collections.singletonMap("tail", "aa/xx"));
            tester.testUnmatched("/a");
            tester.testUnmatched("/a/c");
        }
        {
            Tester tester = new Tester();
//            Object i1 = tester.addPath("/a/b");
            Object i2 = tester.addPath("/a/b/:xx");
            tester.testUnmatched("/a/b");
            tester.test("/a/b/", i2, Collections.singletonMap("xx", ""));
            tester.test("/a/b/aa", i2, Collections.singletonMap("xx", "aa"));
            tester.testUnmatched("/a/b/aa/cc");
        }

    }

    @Test
    public void builder3() {
        {
            Tester tester = new Tester();
            Object i1 = tester.addPath("/");
            Object i2 = tester.addPath("/*");
            tester.test("/a/b", i2);
            tester.test("//", i1);
            tester.test("/", i1);
        }

        {
            Tester tester = new Tester();
            Object i1 = tester.addPath("//a///b///*");
            Object i2 = tester.addPath("/*");
            Object i3 = tester.addPath("/cc/dd/:ee/*tail");
            tester.test("/a/b", i1);
            tester.test("//", i2);
            tester.test("/", i2);
            tester.test("/cc/dd/ee1/", i3, new HashMap<String, String>() {
                {
                    put("ee", "ee1");
                    put("tail", "");
                }
            });
        }

    }

    @Test
    public void builder4() {
        {
            Tester tester = new Tester();
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < 100; j++) {
                    String pathPattern = "/a/" + i + "/b/" + j;
                    Object object = tester.addPath(pathPattern);
                    m.put(pathPattern, object);
                }
            }

            for (int i = 0; i < 1000; i++) {
                for (Map.Entry<String, Object> entry : m.entrySet()) {
                    tester.test(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}