package lua.test;

import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.Script;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.test.TestInIOThreadParent;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestDeadCode extends TestInIOThreadParent {

    @Test
    public void t() throws Exception {
        String resourceStr = getResourceStr("/deadCode1.js");
        MyLib myLib = new MyLib();

        String[] split = resourceStr.split("====================");
        for (String s : split) {
            String trim = s.trim();
            if (trim.isEmpty()) {
                continue;
            }

            try {
                Script compiled = Script.compile(trim, myLib);
            } catch (ParseException e) {
                String message = e.getMessage();
                if (StringUtils.isEmpty(message) || !message.contains("dead code")) {
                    throw e;
                }
                int i = message.indexOf('@');
                int codePos = Integer.parseInt(message.substring(i + 1).trim());
                System.out.println("DEAD_CODE:\n\t" + trim.substring(codePos));
                e.printStackTrace(System.out);
                continue;
            }
            Assert.fail();
        }
    }

}
