package io.fiber.net.test;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@RunWith(IOThreadRunner.class)
public class TestInIOThreadParent {

    @Ignore
    protected String getResourceStr(String path) throws IOException {
        try (InputStream resource = getClass().getResourceAsStream(path)) {
            return IOUtils.toString(resource);
        }
    }
}
