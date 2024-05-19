package io.fiber.net.dubbo.nacos;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONB;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.Script;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.ScriptExceptionNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JsonNodeForFastJson2Test {
    @Before
    public void b() throws Exception {
        Class.forName("io.fiber.net.dubbo.nacos.DubboClient", true, JsonNodeForFastJson2Test.class.getClassLoader());
    }

    @Test
    public void t() throws Exception {
        String jsonString = JSON.toJSONString(TextNode.valueOf("13=121321312"));
        Assert.assertNotNull("13=121321312", jsonString);

        Script.compileWithoutOptimization("try{1/0;}catch(e){return e;}")
                .aotExec(NullNode.getInstance(), null)
                .subscribe((jsonNode, throwable) -> {
                    Assert.assertNotNull(JSON.toJSONBytes(jsonNode));
                    String s = JSON.toJSONString(jsonNode);
                    Assert.assertNotNull(s);
                    System.out.println(s);
                });

        Script.compileWithoutOptimization("throw \"error\";")
                .aotExec(NullNode.getInstance(), null)
                .subscribe((jsonNode, throwable) -> {
                    Assert.assertNull(jsonNode);
                    Assert.assertNotNull(throwable);
                    String jsonString1 = JSON.toJSONString(ScriptExceptionNode.of((ScriptExecException) throwable));
                    System.out.println(jsonString1);
                    Assert.assertEquals("{\"code\":500,\"name\":\"execute scripe error\",\"message\":\"EXEC_UNKNOWN_ERROR\",\"meta\":\"error\"}", jsonString1);
                });
    }

    @Test
    public void t2() throws Exception {
        String json = "{\"code\":500,\"name\":\"SCRIPT_EVALUATION\",\"message\":\"EL1082:compute error in '1' '*' '0'\"}";
        JsonNode node = JsonUtil.readTree(json);
        byte[] jsonBytes = JSON.toJSONBytes(node);
        Assert.assertEquals(json, new String(jsonBytes));

        byte[] bytes = JSONB.toBytes(node);
        Assert.assertEquals(json, JSON.toJSONString(JSONB.parseObject(bytes)));
    }
}