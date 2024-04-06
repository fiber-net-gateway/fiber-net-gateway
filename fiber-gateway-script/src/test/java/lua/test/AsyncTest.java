package lua.test;

import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.Script;
import io.fiber.net.test.TestInIOThreadParent;
import org.junit.Test;

public class AsyncTest extends TestInIOThreadParent {

    @Test
    public void t1() throws Exception {

        String string = getResourceStr("/async.js");


        Script compile = Script.compile(string, new MyLib());

        compile.exec(NullNode.getInstance()).subscribe((node, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
            } else {
                System.out.println("执行结果:" + node);
                System.out.println(string.substring(202, 215));
            }
        });

    }
    @Test
    public void t2() throws Exception {

        String string = getResourceStr("/break_for.js");


        Script compile = Script.compile(string, new MyLib());

        compile.exec(NullNode.getInstance()).subscribe((node, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
            } else {
                System.out.println("执行结果:" + node);
            }
        });

    }

}
