package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.Fiber;
import io.fiber.net.common.utils.SystemPropertyUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

public class ReqHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReqHandler.class);
    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE_CLOSE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse CONTINUE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    static final String X_POWER_BY;

    static {
        X_POWER_BY = "fiber-net(" + SystemPropertyUtil.get(Constant.APP_NAME, "fn") + ")/" + Fiber.VERSION + "/" + Fiber.GIT_HASH;
    }

    static final AsciiString X_POWERED_BY_VALUE = AsciiString.cached(X_POWER_BY);
    private static final FiberException ABORT_REQ_BODY = new FiberException("client close stream on request body receiving", 400, "ABORT_REQ_ERROR");
    private static final FiberException ABORT_ERR = new FiberException("client close stream on request body receiving", 500, "REQ_ERROR");


    static {
        EXPECTATION_FAILED.headers().set("X-Fiber-Status", "invalid-request");
        EXPECTATION_FAILED.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        EXPECTATION_FAILED.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        TOO_LARGE_CLOSE.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    }

    static boolean isUnsupportedExpectation(HttpRequest message) {
        if (message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        final String expectValue = message.headers().get(HttpHeaderNames.EXPECT);
        return expectValue != null && !HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(expectValue);
    }

    private final Date tmpDate = new Date();
    private final long defaultMaxBodyLength;
    private final HttpEngine engine;
    private final AsciiString echoServer;
    private final HttpServerCodec serverCodec;
    private final HttpServerKeepAliveHandler keepAliveHandler;

    private ChannelHandlerContext ctx;
    private HttpExchangeImpl readingExchange;
    private HttpExchangeImpl invokingHead;
    private HttpExchangeImpl invokingTail;
    private boolean connClosing;

    private boolean chunkedBody;
    private long currentReqBodySize = Long.MIN_VALUE;
    private long maxLength;

    private int readBodySize;
    private Scheduler scheduler;

    public ReqHandler(long maxLength, HttpEngine engine, AsciiString echoServer, HttpServerCodec serverCodec, HttpServerKeepAliveHandler keepAliveHandler) {
        this.defaultMaxBodyLength = maxLength;
        this.engine = engine;
        this.echoServer = echoServer;
        this.serverCodec = serverCodec;
        this.keepAliveHandler = keepAliveHandler;
    }

    public long getDefaultMaxBodyLength() {
        return defaultMaxBodyLength;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        scheduler = Scheduler.current();
        this.ctx = ctx;
    }


    public Scheduler getScheduler() {
        return scheduler;
    }

    public Channel getChannel() {
        return ctx.channel();
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        HttpExchangeImpl httpExchange;
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            FullHttpResponse noEngineResp;
            if ((noEngineResp = notEngineResponse(httpRequest)) == null) {
                HttpExchangeImpl tail;
                if ((tail = readingExchange) != null) {
                    // 上一个请求还没 写完 response，下一个请求就法过来了。
                    logger.error("last request not completed");
                    ReferenceCountUtil.release(msg);
                    tail.abortBody(ABORT_ERR);
                    getChannel().close();
                    return;
                }

                if (invokingHead != null) {
                    // pause reading.
                    ctx.channel().config().setAutoRead(false);
                }

                try {
                    httpExchange = readingExchange = new HttpExchangeImpl(httpRequest, this);
                } catch (Throwable e) {
                    logger.error("error create http exchange", e);
                    ctx.writeAndFlush(EXPECTATION_FAILED);
                    return;
                }
                if (httpExchange.continue100 = HttpUtil.is100ContinueExpected(httpRequest)) {
                    httpRequest.headers().remove(HttpHeaderNames.EXPECT);
                }

                if (!(httpExchange.connUpgrade = currentReqBodySize == -1L && !chunkedBody)) {
                    // post for engine
                    postForEngine(httpExchange);
                }
            } else {
                ctx.writeAndFlush(noEngineResp.retainedDuplicate(), ctx.voidPromise());
                return;
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;

            if ((httpExchange = this.readingExchange) == null) {
                ReferenceCountUtil.release(content);
                return;
            }

            if (tryFeedContent(content, httpExchange)) {
                if (!httpExchange.prepareInvoke) {
                    postForEngine(httpExchange);
                }
                requestEnd();
            }
            return;
        }
        if (msg instanceof HttpObject) {
            ReferenceCountUtil.release(msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    private void postForEngine(HttpExchangeImpl exchange) {
        if (!exchange.prepareInvoke) {
            exchange.prepareInvoke = true;
            HttpExchangeImpl t;
            if ((t = this.invokingTail) == null) {
                invokingHead = this.invokingTail = exchange;
                invokeEngine(exchange);
            } else {
                invokingTail = t.next = exchange;
            }
        }
    }

    private void invokeEngine(HttpExchangeImpl httpExchange) {
        httpExchange.requestInvoked = true;
        try {
            engine.run(httpExchange);
            httpExchange.doCheckBodySize();
        } catch (Throwable e) {
            logger.error("error run engine", e);
            httpExchange.discardReqBody();
            httpExchange.writeJson(500, "RUN_ENGINE_ERROR");
        }
    }

    void prepareUpgrade() {
        ctx.pipeline().remove(keepAliveHandler);
    }

    void upgrade() {
        serverCodec.upgradeFrom(ctx);
        ctx.pipeline().remove(this);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        execPendingRequest(null);
    }

    boolean checkBody(HttpExchangeImpl exchange, long maxBodyLength) {
        if (readingExchange != exchange) {
            return false;
        }

        this.maxLength = maxBodyLength;
        long cl = currentReqBodySize;
        if (exchange.continue100) {
            if (maxBodyLength > 0 && cl > maxBodyLength) {
                return true;
            }
            ctx.writeAndFlush(CONTINUE.retainedDuplicate(), ctx.voidPromise());
            return false;
        }

        return maxBodyLength > 0 && cl > maxBodyLength;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        execPendingRequest(null);
    }

    void invokeNext(HttpExchangeImpl httpExchange) {
        if (connClosing) {
            return;
        }

        HttpExchangeImpl n;
        if (invokingHead == httpExchange) {
            if ((n = httpExchange.next) == null) {
                invokingHead = invokingTail = null;
            } else {
                invokingHead = n;
                invokeEngine(n);
                ChannelConfig config;
                if ((!n.receiveCompleted || n.next == null) && !(config = ctx.channel().config()).isAutoRead()) {
                    config.setAutoRead(true);
                }
            }
        } else {
            logger.error("[bug]invoking head matched???");
        }
    }

    private void execPendingRequest(Throwable err) {
        connClosing = true;
        HttpExchangeImpl reading = readingExchange;
        readingExchange = null;

        if (reading != null && !reading.receiveCompleted) {
            if (err == null) {
                err = ABORT_REQ_BODY;
            } else {
                err = new FiberException("connection error on reading request body:" + err.getMessage(),
                        400, "REQ_CONN_ERROR");
            }
            reading.abortBody(err);
            if (!reading.prepareInvoke) {
                postForEngine(reading);
            }
        }

        HttpExchangeImpl head = invokingHead;
        invokingHead = invokingTail = null;
        while (head != null) {
            HttpExchangeImpl n = head.next;
            head.invokeClientClosed();
            if (!head.requestInvoked) {
                invokeEngine(head);
            }
            head.discardReqBody();
            head.next = null;
            head = n;
        }
    }

    private void requestEnd() {
        this.readingExchange = null;
        this.readBodySize = 0;
        this.chunkedBody = false;
        this.currentReqBodySize = Long.MIN_VALUE;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof IOException)) {
            logger.error("error in http server IO socket", cause);
        }
        execPendingRequest(cause);
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
            long m;
            if (chunkedBody && (m = maxLength) > 0 && (readBodySize += size) > m) {
                ReferenceCountUtil.release(content);
                httpExchange.abortBody(HttpExchangeImpl.TOO_LARGE_BODY_ERROR);
                return true;
            }
        }

        boolean last = content instanceof LastHttpContent;
        httpExchange.feedReqBody(content.content(), last);
        return last;
    }

    private FullHttpResponse notEngineResponse(HttpRequest request) {

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

        currentReqBodySize = cl;
        chunkedBody = cl == -1L && HttpUtil.isTransferEncodingChunked(request);
        return null;
    }

    void addConnectionHeader(DefaultHttpHeaders headers) {
        Date tmpDate = this.tmpDate;
        tmpDate.setTime(System.currentTimeMillis());
        headers.set(HttpHeaderNames.SERVER, echoServer);
        headers.set(HttpHeaderNames.DATE, DateFormatter.format(tmpDate));
    }

}