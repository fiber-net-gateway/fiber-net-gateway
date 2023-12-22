package io.fiber.net.dubbo.nacos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.utils.JsonUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

public final class DubboReference {
    private static final JsonNode[] EMPTY = new JsonNode[0];
    private final GenericService genericService;
    private final DubboClient.Service ref;
    private final Long timeout;

    public DubboReference(DubboClient.Service service, long timeout) {
        this.ref = service;
        this.genericService = service.genericService;
        this.timeout = timeout;
    }

    public Single<JsonNode> invoke(String methodName, JsonNode[] args) {
        return Single.create(emitter -> {
            RpcContext.getClientAttachment().setObjectAttachment(CommonConstants.TIMEOUT_KEY, timeout);
            genericService.$invokeAsync(methodName, null, args).whenComplete((o, throwable) -> {
                if (throwable != null) {
                    emitter.onError(throwable);
                } else {
                    emitter.onSuccess(o);
                }
            });
        }).map(o -> {
            if (o instanceof JsonNode) {
                return (JsonNode) o;
            } else if (o != null) {
                return JsonUtil.MAPPER.valueToTree(o);
            } else {
                return NullNode.getInstance();
            }
        });
    }

    public Single<JsonNode> invoke(String methodName, ArrayNode args) {
        int size;
        if (args != null && (size = args.size()) > 0) {
            JsonNode[] nodes = new JsonNode[size];
            for (int i = 0; i < size; i++) {
                nodes[i] = args.get(i);
            }
            return invoke(methodName, nodes);
        } else {
            return invoke(methodName, EMPTY);
        }
    }

    public void deRef() {
        ref.destroy();
    }

}
