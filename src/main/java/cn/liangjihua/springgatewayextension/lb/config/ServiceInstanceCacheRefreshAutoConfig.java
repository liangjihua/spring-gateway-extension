package cn.liangjihua.springgatewayextension.lb.config;

import cn.liangjihua.springgatewayextension.lb.ServiceDeRegistration;
import cn.liangjihua.springgatewayextension.lb.ServiceInstanceCacheRefresh;
import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.loadbalancer.cache.enabled", matchIfMissing = true)
@ConditionalOnClass(NacosAutoServiceRegistration.class)
@Slf4j
public class ServiceInstanceCacheRefreshAutoConfig {

    @Value("${service.deregister.wait-millis:#{null}}")
    private Long waitMillis;

    @Bean
    ServiceInstanceCacheRefresh serviceInstanceCacheRefresh(ApplicationContext context) {
        return new ServiceInstanceCacheRefresh(context);
    }

    @Bean
    ServiceDeRegistration serviceDeRegistration(ServiceRegistry<Registration> serviceRegistry,
                                                Registration registration) {
        if (waitMillis != null) {
            return new ServiceDeRegistration(serviceRegistry, registration, waitMillis);
        } else {
            return new ServiceDeRegistration(serviceRegistry, registration);
        }
    }


}
