package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.Fiber;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

public class ReqHandler extends ChannelDuplexHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ReqHandler.class);
    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    static final String X_POWER_BY;

    static {
        X_POWER_BY = "fiber-net(" + SystemPropertyUtil.get(Constant.APP_NAME, "fn") + ")/" + Fiber.VERSION + "/" + Fiber.GIT_HASH;
    }

    private static final AsciiString X_POWERED_BY_VALUE = AsciiString.cached(X_POWER_BY);


    static {
        EXPECTATION_FAILED.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        EXPECTATION_FAILED.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        TOO_LARGE.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        TOO_LARGE.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    }

    static boolean isUnsupportedExpectation(HttpRequest message) {
        if (message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        final String expectValue = message.headers().get(HttpHeaderNames.EXPECT);
        return expectValue != null && !HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(expectValue);
    }


    private final int maxLength;
    private final HttpEngine engine;
    private HttpExchangeImpl httpExchange;
    private boolean chunkedBody;
    private int readBodySize;
    private Scheduler scheduler;

    public ReqHandler(int maxLength, HttpEngine engine) {
        this.maxLength = maxLength;
        this.engine = engine;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        scheduler = Scheduler.current();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            FullHttpResponse noEngineResp;
            if ((noEngineResp = notEngineResponse(httpRequest, ctx)) == null) {
                try {
                    httpExchange = new HttpExchangeImpl(ctx.channel(), httpRequest, scheduler);
                } catch (Throwable e) {
                    ctx.writeAndFlush(EXPECTATION_FAILED);
                    return;
                }

                try {
                    engine.run(httpExchange);
                } catch (Throwable e) {
                    logger.error("error run engine", e);
                    httpExchange.writeJson(500, "RUN_ENGINE_ERROR");
                    httpExchange = null;
                    ReferenceCountUtil.release(msg);
                    return;
                }
            } else {
                ctx.writeAndFlush(noEngineResp.retainedDuplicate(), ctx.voidPromise());
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;

            FullHttpResponse noEngineResp;
            HttpExchangeImpl httpExchange;
            if ((httpExchange = this.httpExchange) == null) {
                ReferenceCountUtil.release(content);
                return;
            }

            if (tryFeedContent(content, httpExchange)) {
                requestEnd();
            }

        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (httpExchange != null) {
            httpExchange.abortBody(new FiberException("client close stream on request body receiving", 400, "REQ_ERROR"));
            requestEnd();
        }
    }

    private void requestEnd() {
        this.readBodySize = 0;
        this.chunkedBody = false;
        this.httpExchange = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (httpExchange != null) {
            httpExchange.abortBody(cause);
            requestEnd();
            logger.error("error in io handler", cause);
        }
    }

    private boolean tryFeedContent(HttpContent content, HttpExchangeImpl httpExchange) {
        DecoderResult decoderResult = content.decoderResult();
        if (decoderResult.isFailure()) {
            ReferenceCountUtil.release(content);
            httpExchange.abortBody(decoderResult.cause());
            return true;
        }

        int size = content.content().readableBytes();
        if (size > 0) {
            if (chunkedBody && (readBodySize += size) > maxLength) {
                ReferenceCountUtil.release(content);
                httpExchange.abortBody(new FiberException("BODY_TOO_LARGE", 413, "body is too large"));
                return true;
            }
        }

        boolean last = content instanceof LastHttpContent;
        httpExchange.feedReqBody(content.content(), last);
        return last;
    }

    private FullHttpResponse notEngineResponse(HttpRequest request, ChannelHandlerContext ctx) {
        if (httpExchange != null) {
            return EXPECTATION_FAILED;
        }

        if (request.decoderResult().isFailure()) {
            return EXPECTATION_FAILED;
        }

        if (isUnsupportedExpectation(request)) {
            return EXPECTATION_FAILED;
        }

        long cl;
        try {
            cl = HttpUtil.getContentLength(request, -1L);
        } catch (RuntimeException e) {
            return EXPECTATION_FAILED;
        }

        if (HttpUtil.is100ContinueExpected(request)) {
            if (cl > maxLength) {
                return TOO_LARGE;
            }
            ctx.writeAndFlush(CONTINUE.retainedDuplicate(), ctx.voidPromise());
            return null;
        }

        if (cl > maxLength) {
            return TOO_LARGE;
        } else if (cl == -1) {
            chunkedBody = true;
        }

        return null;
    }

    static void addConnectionHeader(DefaultHttpHeaders headers) {
        headers.set(HttpHeaderNames.DATE, DateFormatter.format(new Date()));
        headers.set(HttpHeaderNames.SERVER, X_POWERED_BY_VALUE);
    }

}