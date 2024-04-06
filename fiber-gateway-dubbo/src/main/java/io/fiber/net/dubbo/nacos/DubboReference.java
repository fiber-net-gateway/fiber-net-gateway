package io.fiber.net.dubbo.nacos;

import io.fiber.net.common.async.Single;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.JsonUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

public final class DubboReference {
    static final Object[] EMPTY = new Object[0];
    private final GenericService genericService;
    private final DubboClient.Service ref;
    private final Long timeout;

    DubboReference(DubboClient.Service service, long timeout) {
        this.ref = service;
        this.genericService = service.genericService;
        this.timeout = timeout;
    }

    public Single<JsonNode> invoke(String methodName, JsonNode[] args) {
        if (ArrayUtils.isEmpty(args)) {
            return invoke(methodName, EMPTY);
        } else {
            Object[] objects = new Object[args.length];
            System.arraycopy(args, 0, objects, 0, args.length);
            return invoke(methodName, objects);
        }
    }


    public Single<JsonNode> invoke(String methodName, Object[] args) {
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
                return JsonUtil.valueToTree(o);
            } else {
                return NullNode.getInstance();
            }
        });
    }

    public Single<JsonNode> invoke(String methodName, ArrayNode args) {
        int size;
        if (args != null && (size = args.size()) > 0) {
            Object[] nodes = new Object[size];
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
