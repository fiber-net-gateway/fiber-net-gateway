package io.fiber.net.common.utils;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.json.ExceptionNode;
import io.fiber.net.common.json.JsonNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JsonNodeTest {

    public static class Model {
        private String name;
        private int age;
        private List<String> f = new ArrayList<>();

        public Model() {
        }

        public Model(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public List<String> getF() {
            return f;
        }

        public void setF(List<String> f) {
            this.f = f;
        }
    }

    @Test
    public void t() throws Exception {
        Model aa = new Model("aa", 123);
        aa.getF().add("123");
        com.fasterxml.jackson.databind.JsonNode node = JsonUtil.MAPPER.convertValue(aa,
                com.fasterxml.jackson.databind.JsonNode.class);
        JsonNode node1 = JsonUtil.MAPPER.convertValue(aa,
                JsonNode.class);

        Assert.assertEquals(JsonUtil.MAPPER.writeValueAsString(node), JsonUtil.MAPPER.writeValueAsString(node1));

        Assert.assertEquals(JsonUtil.MAPPER.readValue(JsonUtil.MAPPER.writeValueAsString(node1),
                com.fasterxml.jackson.databind.JsonNode.class), node);
        System.out.println(node);
        System.out.println(node1);

    }

    @Test
    public void t2() throws Exception {

        ExceptionNode n = new ExceptionNode(new FiberException("mseesgf", 300, "ERROR"));
        String s = JsonUtil.MAPPER.writeValueAsString(n);
        System.out.println(s);
    }
}
