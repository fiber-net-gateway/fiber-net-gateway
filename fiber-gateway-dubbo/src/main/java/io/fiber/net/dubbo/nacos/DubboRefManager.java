package io.fiber.net.dubbo.nacos;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.Single;
import io.fiber.net.common.ioc.Destroyable;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.JsonUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.ArrayList;
import java.util.List;

public class DubboRefManager implements Destroyable {
    static final String DUBBO_ERR_NAME = "DUBBO_INVOCATION";
    static final Object[] EMPTY = Constant.EMPTY_ARRAY;

    private final Injector injector;

    private DubboClient client;
    private List<Reference> services;

    public DubboRefManager(Injector injector) {
        this.injector = injector;
    }

    public Reference ref(String service, long timeout) {
        DubboClient dc = client;
        if (dc == null) {
            dc = client = injector.getInstance(DubboClient.class);
            services = new ArrayList<>();
        }

        DubboClient.Service ds = dc.getOrCreate(service);
        Reference e = new Reference(ds, timeout);
        services.add(e);
        return e;
    }

    @Override
    public void destroy() {
        if (services != null) {
            for (Reference service : services) {
                service.ref.destroy();
            }
        }
    }

    public static final class Reference {
        private final GenericService genericService;
        private final DubboClient.Service ref;
        private final Long timeout;

        Reference(DubboClient.Service service, long timeout) {
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
            return Single.<JsonNode>create(emitter -> {
                        RpcContext.getClientAttachment().setObjectAttachment(CommonConstants.TIMEOUT_KEY, timeout);
                        genericService.$invokeAsync(methodName, null, args)
                                .whenComplete((o, throwable) -> {
                                    if (throwable != null) {
                                        emitter.onError(throwable);
                                    } else if (o instanceof JsonNode) {
                                        emitter.onSuccess((JsonNode) o);
                                    } else if (o != null) {
                                        emitter.onSuccess(JsonUtil.valueToTree(o));
                                    } else {
                                        emitter.onSuccess(NullNode.getInstance());
                                    }
                                });
                    }).mapError(e -> new FiberException(e.getMessage(), e, 500, DUBBO_ERR_NAME))
                    .notifyOn(Scheduler.current());
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
    }

}
