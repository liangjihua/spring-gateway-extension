package cn.liangjihua.springgatewayextension.lb;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.liangjihua.springgatewayextension.lb.chooser.IRuleChooser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.liangjihua.springgatewayextension.lb.constants.LbConstants.SERVICE_META_VERSION_KEY;

/**
 * 负载均衡器实现：从指定的版本中选取服务实例
 */
@Slf4j
public class VersionLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    private static final String VERSION_HEADER_KEY = "X-service-version";


    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSuppliers;

    private final String serviceId;

    private final IRuleChooser ruleChooser;

    public VersionLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSuppliers,
                               String serviceId, IRuleChooser ruleChooser) {
        this.serviceInstanceListSuppliers = serviceInstanceListSuppliers;
        this.serviceId = serviceId;
        this.ruleChooser = ruleChooser;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        // 从request中获取版本，兼容webflux方式
        RequestData requestData = ((RequestDataContext) (request.getContext())).getClientRequest();
        String version = getVersionFromRequestData(requestData);
        log.debug("选择的版本号为：{}", version);
        return Objects.requireNonNull(serviceInstanceListSuppliers.getIfAvailable())
            .get(request)
            .next()
            .map(instanceList -> getInstanceResponse(instanceList, version));
    }

    private String getVersionFromRequestData(RequestData requestData) {
        Map<String, String> queryMap = HttpUtil.decodeParamMap(requestData.getUrl().toString(), StandardCharsets.UTF_8);
        if (MapUtil.isNotEmpty(queryMap) && queryMap.containsKey(VERSION_HEADER_KEY)
            && StrUtil.isNotBlank(queryMap.get(VERSION_HEADER_KEY))) {
            return queryMap.get(VERSION_HEADER_KEY);
        } else if (requestData.getHeaders().containsKey(VERSION_HEADER_KEY)) {
            List<String> versions = requestData.getHeaders().get(VERSION_HEADER_KEY);
            return versions != null ? versions.get(0) : null;
        }
        return null;
    }

    /**
     * 返回与版本号关联的实例.
     * </p>
     * 版本号为空时，优先本地实例，否则选择无版本号的实例。但是若版本号不为空，并且
     * 没有与版本号关联的实例，则会优先 fallback 到本地实例，无本地实例则 fallback 到无版本号的实例。
     * 若存在多个符合规则的实例，则通过 {@link IRuleChooser} 选取要使用的实例.
     *
     * @param instances 所有实例列表
     * @param version 版本号
     * @return 与版本号关联的实例，或 EmptyResponse
     */
    Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> instances, String version) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (ServiceInstance instance : instances) {
                sb.append(instance.getHost());
                sb.append(":");
                sb.append(instance.getPort());
                sb.append(" ;");
            }
            log.debug("服务列表：{}", sb);
        }


        List<ServiceInstance> candidateInstances = getCandidateInstances(version, instances);

        ServiceInstance serviceInstance = this.ruleChooser.choose(candidateInstances);
        if (!Objects.isNull(serviceInstance)) {
            log.debug("使用serviceId为：{}服务， 选择version为：{}， 地址：{}:{}，", serviceId, version,
                serviceInstance.getHost(), serviceInstance.getPort());
            return new DefaultResponse(serviceInstance);
        }
        return new EmptyResponse();
    }

    private static List<ServiceInstance> getCandidateInstances(String version,
                                                               List<ServiceInstance> instances) {

        List<ServiceInstance> nonVersionedInstances = instances.stream()
                .filter(s -> !s.getMetadata().containsKey(SERVICE_META_VERSION_KEY))
                .toList();

        List<ServiceInstance> versionedInstances = instances.stream()
                .filter(instance -> instance.getMetadata().containsKey(SERVICE_META_VERSION_KEY))
                .toList();

        List<ServiceInstance> hostInstances = getHostInstances(instances);

        if (StrUtil.isBlank(version) && CollUtil.isEmpty(hostInstances)) {
            return nonVersionedInstances;
        }

        if (StrUtil.isNotBlank(version)) {
            List<ServiceInstance> candidateInstances = versionedInstances.stream()
                .filter(instance -> version.equals(instance.getMetadata().get(SERVICE_META_VERSION_KEY)))
                .toList();
            if (candidateInstances.isEmpty()) {
                return CollUtil.isNotEmpty(hostInstances) ? hostInstances : nonVersionedInstances;
            }
            return candidateInstances;
        } else {
            return hostInstances;
        }
    }

    private static List<ServiceInstance> getHostInstances(List<ServiceInstance> instances) {
        String host = Objects.nonNull(NetUtil.getLocalhost())
                ? ObjectUtil.defaultIfNull(NetUtil.getLocalhost().getHostAddress(), "") : "";
        return instances.stream()
                .filter(instance -> host.equals(instance.getHost()))
                .toList();
    }
}
