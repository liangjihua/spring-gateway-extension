package cn.liangjihua.springgatewayextension.lb;

import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheManager;
import org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplierBuilder;
import org.springframework.context.ApplicationContext;

import static org.springframework.cloud.loadbalancer.config.LoadBalancerCacheAutoConfiguration.*;
import static org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier.SERVICE_INSTANCE_CACHE_NAME;

/**
 * 配置 spring cloud loadbalancer 缓存刷新.
 * <p></p>
 * spring cloud loadbalancer 默认对于服务实例信息会进行缓存，
 * 这个缓存的时间默认为 35 秒{@link LoadBalancerCacheProperties#getTtl}。
 * 这会导致在服务实例上下线的时候，spring cloud gateway
 * 不能及时的获取到最新的服务实例列表，可能会将请求转发至已经下线的服务实例中，
 * 导致请求失败。
 * <p></p>
 * 这个配置类向 nacos 客户端的事件中心注册了一个事件监听器，监听
 * nacos 的服务实例变化事件，刷新 spring cloud loadbalancer 中
 * 对应的服务实例列表缓存，从而使 spring cloud gateway 能及时
 * 获取最新的服务实例.
 * <p></p>
 * 注意，此配置也不能保证实时同步服务实例的信息，这是由于 nacos 客户端与
 * nacos 服务器之间的信息同步也有延迟。nacos 客户端存在一个轮询机制，会
 * 以固定频率持续的拉取最新的服务实例消息，这个频率默认是 10s。所以服务
 * 实例的信息还是会有10秒的延迟。这个间隔可以使用 nacos 服务器的 openapi
 * 进行调整：<code>PUT /nacos/v1/ns/operator/switches?entry=defaultCacheMillis&value=1000</code>
 * <p></p>
 * nacos 提供了基于 udp 的消息推送机制，用于实时的推送服务实例信息，不过
 * 由于nacos 使用了随机的 udp 端口号，这导致在部署在容器中时，该特性基本不可用，并且由于是 udp
 * 消息推送失败也完全不会有任何异常日志信息。 nacos 2.0 提供了 grpc 的消息推送机制，
 * 相比 udp 使用起来更方便，后期可以考虑升级到 nacos 2.0 以使用该特性.
 */
@Slf4j
public class ServiceInstanceCacheRefresh implements InitializingBean {

    private final ApplicationContext context;

    public ServiceInstanceCacheRefresh(ApplicationContext context) {
        this.context = context;
    }

    /**
     * 将 Spring cloud gateway 缓存刷新注册到 nacos 消息中心.
     * <p></p>
     * 这里不能使用 spring 属性注入 LoadBalancerCacheManager，
     * 因为 LoadBalancerCacheManager bean
     * 在创建时被配置为 autowireCandidate = false,
     * 这禁止了 spring 在解析 bean 依赖时将其列为候选者，
     * 所以是无法通过 spring 属性注入方式来获取
     * LoadBalancerCacheManager bean 的实例
     *
     * @see DefaultLoadBalancerCacheManagerConfiguration#defaultLoadBalancerCacheManager
     * @see ServiceInstanceListSupplierBuilder#withCaching
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public void afterPropertiesSet() {
        ObjectProvider<LoadBalancerCacheManager> cacheManagerProvider = context
            .getBeanProvider(LoadBalancerCacheManager.class);
        if (cacheManagerProvider.getIfAvailable() != null) {
            LoadBalancerCacheRefresh refresh = new LoadBalancerCacheRefresh(cacheManagerProvider.getIfAvailable());
            NotifyCenter.registerSubscriber(refresh);
        }
    }


    public static class LoadBalancerCacheRefresh extends Subscriber<InstancesChangeEvent> {
        private final LoadBalancerCacheManager defaultLoadBalancerCacheManager;

        public LoadBalancerCacheRefresh(LoadBalancerCacheManager defaultLoadBalancerCacheManager) {
            this.defaultLoadBalancerCacheManager = defaultLoadBalancerCacheManager;
        }


        public void onEvent(InstancesChangeEvent event) {
            if (log.isDebugEnabled()) {
                log.debug("接收 nacos 服务实例变更事件：{}, 开始刷新 Spring cloud loadbalancer 服务缓存",
                    JacksonUtils.toJson(event));
            }
            Cache cache = defaultLoadBalancerCacheManager.getCache(SERVICE_INSTANCE_CACHE_NAME);
            if (cache != null) {
                String serviceName = stripServiceName(event.getServiceName());
                cache.evictIfPresent(serviceName);
                log.debug("Spring cloud loadbalancer 服务缓存刷新完成");
            }
        }

        @Override
        public Class<? extends Event> subscribeType() {
            return InstancesChangeEvent.class;
        }

        private String stripServiceName(String serviceName) {
            if (StringUtils.isNotBlank(serviceName)) {
                return serviceName.substring(serviceName.lastIndexOf("@") + 1);
            }
            return serviceName;
        }
    }
}
