package io.fiber.net.dubbo.nacos;

import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class DubboClientTest {
    @Test
    public void t1() {
        DubboConfig dubboConfig = new DubboConfig();
        dubboConfig.setRegistryAddr("nacos://172.22.94.85:8848");
        DubboClient dubboClient = new DubboClient(dubboConfig);
        DubboReference ref = dubboClient.getRef("com.test.dubbo.DemoService", 10000);
        NioEventLoopGroup group = new NioEventLoopGroup();
        group.execute(() -> {
            ref.invoke("createUser", new Object[]{TextNode.valueOf("ccccc")})
                    .subscribe((jsonNode, throwable) -> {
                        System.out.println(jsonNode);
                        System.err.println(throwable);
                    });
        });
        group.shutdownGracefully(10000, 10000, TimeUnit.MILLISECONDS);
    }
}
