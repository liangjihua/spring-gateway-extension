package cn.liangjihua.springgatewayextension.lb;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;

import java.util.Map;

import static cn.liangjihua.springgatewayextension.lb.constants.LbConstants.SERVICE_META_VERSION_KEY;

/**
 * 一个 LoadBalancerLifecycle 的实现，将实际处理请求的服务实例的标识添加到 response header 中.
 * <p></p>
 * 此类自定义了一个 http header: X-Backend-Version。
 * 这在开发时非常方便，在具有多个不同版本的服务实例时，调用者可以清晰的知道自己发起的请求
 * 到底被哪一个实例处理了。
 *
 * @see VersionLoadBalancer
 */
public class VersionLoadBalancerLifecycle
        implements LoadBalancerLifecycle<RequestDataContext, ResponseData, ServiceInstance> {
    public static final String HEADER_BACKEND_VERSION = "X-Backend-Version";

    @Override
    public void onStart(Request<RequestDataContext> request) {

    }

    @Override
    public void onStartRequest(Request<RequestDataContext> request, Response<ServiceInstance> lbResponse) {
    }

    @Override
    public void onComplete(CompletionContext<ResponseData, ServiceInstance, RequestDataContext> completionContext) {
        Response<ServiceInstance> loadBalancerResponse = completionContext.getLoadBalancerResponse();
        if (loadBalancerResponse == null) {
            return;
        }
        ServiceInstance server = loadBalancerResponse.getServer();
        if (server != null) {
            Map<String, String> metadata = server.getMetadata();
            if (metadata.containsKey(SERVICE_META_VERSION_KEY)) {
                completionContext.getClientResponse().getHeaders()
                        .add(HEADER_BACKEND_VERSION, metadata.get(SERVICE_META_VERSION_KEY));
            }
        }
    }
}
