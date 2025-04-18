package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.ext.RouterNameFetcher;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.FileRegionFactory;
import io.fiber.net.common.utils.StringUtils;
import io.netty.channel.FileRegion;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

public class HttpExchangeTest {

    private static class R implements RouterHandler<HttpExchange> {

        private final FileRegionFactory fileRegionFactory;
        private long size;

        private R() throws Exception {
            Path tempFile = Files.createTempFile("tt", "eee");
            byte[] data = new byte[10000];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) ('0' + i % 10);
            }
            Files.write(tempFile, data, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            tempFile.toFile().deleteOnExit();
            BasicFileAttributes basicFileAttributes = Files.readAttributes(tempFile, BasicFileAttributes.class);
            size = basicFileAttributes.size();
            this.fileRegionFactory = new FileRegionFactory(new RandomAccessFile(tempFile.toFile(), "r")
                    .getChannel());
        }

        @Override
        public String getRouterName() {
            return RouterNameFetcher.DEF_ROUTER_NAME;
        }

        @Override
        public void invoke(HttpExchange exchange) throws Exception {
            exchange.discardReqBody();
            long s = -1;
            long e = -1;


            String range = exchange.getRequestHeader("Range");
            if (StringUtils.isNotEmpty(range)) {
                if (range.startsWith("bytes=")) {
                    range = range.substring(6).trim();
                }
                int i = range.indexOf('-');
                if (i > 0) {
                    s = Long.parseLong(range.substring(0, i));
                }
                if (i < range.length() - 1) {
                    e = Long.parseLong(range.substring(i + 1));
                }
            }
            long totalSpace = size;
            long count = totalSpace;
            if (s == -1) {
                s = 0;
            }
            if (e == -1 || e > count) {
                e = count;
            }

            count = e - s;
            FileRegion fileRegion = fileRegionFactory.createFileRegion(s, count);
            String format = String.format("bytes %d-%d/%d",
                    s, e, totalSpace);
            System.out.println(format);
            int status = 200;
            if (StringUtils.isNotEmpty(range)) {
                exchange.setResponseHeader("Content-Range", format);
                status = 206;
            }
            exchange.writeFileRegion(status, fileRegion);
        }

        @Override
        public void destroy() {
            fileRegionFactory.release();
            int i = fileRegionFactory.refCnt();
            System.out.println("ref= " + i);
        }
    }

    Injector injector;

    //    @Before
    public void before() throws Exception {
        injector = Injector.getRoot().createChild(new EngineModule());
        HttpEngine engine = (HttpEngine) injector.getInstance(Engine.class);
        engine.addHandlerRouter(new R());
        engine.installExt();
    }


    public static void main(String[] args) throws Exception {
        HttpExchangeTest httpExchangeTest = new HttpExchangeTest();
        httpExchangeTest.before();
    }

    //    @After
    public void after() {
        injector.destroy();
    }

}