package io.fiber.net.proxy.gov;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.lib.ReflectDirective;
import io.fiber.net.script.lib.ScriptDirective;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.support.RateLimiter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ScriptDirective("rate_limiter")
public class RateLimiterFunc {
    private final RateLimiter rateLimiter;

    public RateLimiterFunc(String name, int count, Duration duration) {
        rateLimiter = RateLimiter.of(name, duration, count);
    }

    static Library.DirectiveDef getRateLimiterFunc(String name, List<Literal> literals) {
        if (CollectionUtils.isEmpty(literals)) {
            throw new ParseException("rate_limiter require arg");
        }
        JsonNode value = literals.get(0).getLiteralValue();
        if (!value.isTextual()) {
            throw new ParseException("rate_limiter require string arg. like \"10/3s\"");
        }
        String string = value.textValue();
        int i = string.indexOf('/');
        if (i > 0) {
            int count = Integer.parseInt(string.substring(0, i));
            Duration duration = of(string.substring(i + 1));
            return ReflectDirective.of(new RateLimiterFunc(name, count, duration));
        } else {
            throw new ParseException("rate_limiter require string arg. like \"10/3s\"");
        }
    }

    private static Duration of(String duration) {
        byte[] arr = CharArrUtil.toReadOnlyAsciiCharArr(duration);
        ChronoUnit unit = ChronoUnit.MILLIS;
        int value = 0;
        int i;
        for (i = 0; i < arr.length; i++) {
            int n = arr[i];
            if (n >= '0' && n <= '9') {
                n -= '0';
                value = value * 10 + n;
            } else {
                break;
            }
        }
        if (i < arr.length) {
            String s = duration.substring(i);
            if ("s".equals(s)) {
                unit = ChronoUnit.SECONDS;
            } else if ("m".equals(s)) {
                unit = ChronoUnit.MINUTES;
            } else if ("h".equals(s)) {
                unit = ChronoUnit.SECONDS;
            } else if (!"ms".equals(s)) {
                throw new ParseException("rate_limiter require string arg. like \"10/3s\", support h|m|s|ms");
            }
        }
        return Duration.of(value, unit);
    }

    @ScriptFunction(name = "acquire", constExpr = false, params = {
            @ScriptParam(value = "timeoutMs", optional = true, defaultValue = "0")
    })
    public void acquire(Library.AsyncHandle handle, JsonNode timeoutMs) {
        acquire(timeoutMs.asLong(0L), handle, false);
    }

    @ScriptFunction(name = "mustAcquire", constExpr = false, params = {
            @ScriptParam(value = "timeoutMs", optional = true, defaultValue = "0")
    })
    public void mustAcquire(Library.AsyncHandle handle, JsonNode timeoutMs) {
        acquire(timeoutMs.asLong(0L), handle, true);
    }

    private void acquire(long ms, Library.AsyncHandle handle, boolean must) {
        if (ms == 0L) {
            if (rateLimiter.acquirePermission()) {
                handle.returnVal(BooleanNode.TRUE);
            } else if (must) {
                handle.throwErr(new ScriptExecException("rate-limiter blocked", 500, "RATE_LIMITER_BLOCK"));
            } else {
                handle.returnVal(BooleanNode.FALSE);
            }
        } else {
            long l = rateLimiter.blockAcquirePermission(Duration.ofMillis(ms));
            if (l == -1) {
                if (must) {
                    handle.throwErr(new ScriptExecException("rate-limiter blocked", 500, "RATE_LIMITER_BLOCK"));
                } else {
                    handle.returnVal(BooleanNode.FALSE);
                }
            } else if (l == 0) {
                handle.returnVal(BooleanNode.TRUE);
            } else {
                Scheduler.current().scheduleInNano(() -> handle.returnVal(BooleanNode.TRUE), l);
            }
        }
    }
}
