package lua.test;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.run.UnsafeUtil;

public class ShowByteCode {
    public static void t() {
        int a = 1;
        long b = 1;

        for (int i = 0; i < 10; i++) {
            int c = 0;
            System.out.println(i + c);
        }

//        String c = "1";
        System.out.println(1);
    }

    public static void main(String[] args) {
        t();
    }
}
