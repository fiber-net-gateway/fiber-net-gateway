package io.fiber.net.proxy.gov;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.utils.CharArrUtil;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.proxy.lib.HttpDynamicFunc;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.support.RateLimiter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class RateLimiterFunc implements Library.DirectiveDef {
    private final RateLimiter rateLimiter;
    private AcquireFunction aFunction;
    private MustAcquireFunction maFunction;

    public RateLimiterFunc(String name, int count, Duration duration) {
        rateLimiter = RateLimiter.of(name, duration, count);
    }

    static RateLimiterFunc getRateLimiterFunc(String name, List<Literal> literals) {
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
            return new RateLimiterFunc(name, count, duration);
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

    private static class AcquireFunction implements HttpDynamicFunc {
        private final RateLimiter rateLimiter;

        private AcquireFunction(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        @Override
        public void call(ExecutionContext context) {
            long ms = 0L;
            if (!context.noArgs()) {
                ms = context.getArgVal(0).asLong(ms);
            }
            if (ms == 0L) {
                if (rateLimiter.acquirePermission()) {
                    context.returnVal(BooleanNode.TRUE);
                } else {
                    context.returnVal(BooleanNode.FALSE);
                }
            } else {
                long l = rateLimiter.blockAcquirePermission(Duration.ofMillis(ms));
                if (l == -1) {
                    context.returnVal(BooleanNode.FALSE);
                } else if (l == 0) {
                    context.returnVal(BooleanNode.TRUE);
                } else {
                    Scheduler.current().scheduleInNano(() -> context.returnVal(BooleanNode.TRUE), l);
                }
            }
        }
    }

    private static class MustAcquireFunction implements HttpDynamicFunc {
        private final RateLimiter rateLimiter;

        private MustAcquireFunction(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        @Override
        public void call(ExecutionContext context) {
            long ms = 0L;
            if (!context.noArgs()) {
                ms = context.getArgVal(0).asLong(ms);
            }
            if (ms == 0L) {
                if (rateLimiter.acquirePermission()) {
                    context.returnVal(BooleanNode.TRUE);
                } else {
                    context.throwErr(new ScriptExecException("rate-limiter blocked", 500, "RATE_LIMITER_BLOCK"));
                }
            } else {
                long l = rateLimiter.blockAcquirePermission(Duration.ofMillis(ms));
                if (l == -1) {
                    context.throwErr(new ScriptExecException("rate-limiter blocked", 500, "RATE_LIMITER_BLOCK"));
                } else if (l == 0) {
                    context.returnVal(BooleanNode.TRUE);
                } else {
                    Scheduler.current().scheduleInNano(() -> context.returnVal(BooleanNode.TRUE), l);
                }
            }
        }
    }

    @Override
    public Library.Function findFunc(String directive, String function) {
        return null;
    }

    @Override
    public Library.AsyncFunction findAsyncFunc(String directive, String function) {
        if ("acquire".equals(function)) {
            if (aFunction == null) {
                aFunction = new AcquireFunction(rateLimiter);
            }
            return aFunction;
        } else if ("mustAcquire".equals(function)) {
            if (maFunction == null) {
                maFunction = new MustAcquireFunction(rateLimiter);
            }
            return maFunction;
        }
        return null;
    }
}
