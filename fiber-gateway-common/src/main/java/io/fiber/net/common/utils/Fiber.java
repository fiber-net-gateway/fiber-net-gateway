package io.fiber.net.common.utils;

import java.io.InputStream;
import java.util.Properties;

public class Fiber {

    public static final String VERSION;
    public static final String GIT_HASH;
    public static final String BUILD_TIME;
    public static final String GIT_BRANCH;

    static {

        String version = "UNKNOWN", id = "UNKNOWN", time = "UNKNOWN", branch = "UNKNOWN";
        try (InputStream in = Fiber.class.getResourceAsStream("/git.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            //git.commit.id=dev
            //git.branch=master
            //build.version=dev
            //build.time=no_time
            version = properties.getProperty("git.build.version", "UNKNOWN");
            id = properties.getProperty("git.commit.id", "UNKNOWN");
            time = properties.getProperty("git.build.time", "UNKNOWN");
            branch = properties.getProperty("git.branch", "UNKNOWN");
            if (id.length() > 16) {
                id = id.substring(0, 16);
            }
        } catch (Exception ignore) {
        }
        VERSION = version;
        GIT_HASH = id;
        GIT_BRANCH = branch;
        BUILD_TIME = time;
    }

}
