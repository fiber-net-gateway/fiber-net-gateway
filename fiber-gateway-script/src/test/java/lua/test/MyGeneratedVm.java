package lua.test;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ValueNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.run.AbstractVm;
import io.fiber.net.script.run.Access;
import io.fiber.net.script.run.Binaries;

public class MyGeneratedVm extends AbstractVm {
    private static ValueNode _LITERAL_0;
    private static ValueNode _LITERAL_1;
    private static ValueNode _LITERAL_2;
    private int asyncState;
    private JsonNode _stack_0;
    private JsonNode _stack_1;
    private JsonNode _stack_2;
    private JsonNode _stack_3;


    public MyGeneratedVm(JsonNode root, Object attach) {
        super(root, attach);
    }

    @Override
    public JsonNode getArgVal(int idx) {
        return null;
    }

    @Override
    public int getArgCnt() {
        return 0;
    }

    @Override
    public Maybe<JsonNode> exec() {
        return null;
    }

    @Override
    protected void resume() {

    }


    private void run() throws ScriptExecException {
        rtValue = Access.pushArray(Access.pushArray(JsonUtil.createArrayNode(), _LITERAL_0),
                Binaries.plus(_LITERAL_1, Binaries.divide(Binaries.plus(_LITERAL_2, _LITERAL_0), _LITERAL_2))
        );
        state = STAT_END_SEC;
    }


    public static void main(String[] args) {
        System.out.println("");
    }
}
