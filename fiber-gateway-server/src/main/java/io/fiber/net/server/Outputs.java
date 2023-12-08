package io.fiber.net.server;

import io.fiber.net.common.utils.ErrorInfo;
import io.fiber.net.common.utils.JsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Outputs {

    private Outputs() {
    }

    public static int errorResponse(Throwable err, ByteBuf buf) {
        ErrorInfo of = ErrorInfo.of(err);
        try {
            JsonUtil.MAPPER.writeValue((OutputStream) new ByteBufOutputStream(buf), of);
        } catch (Throwable e) {
            buf.clear();
            buf.writeCharSequence(
                    "{\"name\":\"UNKNOWN_WRITE_ERROR\", \"status\":500, \"message\":\"unknown\"}"
                    , StandardCharsets.UTF_8
            );
            return 500;
        }
        return of.getStatus();
    }

}
