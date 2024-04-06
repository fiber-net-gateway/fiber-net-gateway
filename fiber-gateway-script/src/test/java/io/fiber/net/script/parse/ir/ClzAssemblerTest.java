package io.fiber.net.script.parse.ir;

import org.junit.Test;

import static org.junit.Assert.*;

public class ClzAssemblerTest {
    @Test
    public void t() throws Exception {
        System.out.println(ClzAssembler.getBinariesTypeName());
        this.getClass().getClassLoader().loadClass(ClzAssembler.class.getName());

        System.out.println(ClzAssembler.class);
    }

}