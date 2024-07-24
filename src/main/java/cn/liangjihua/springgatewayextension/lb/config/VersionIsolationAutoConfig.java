package cn.liangjihua.springgatewayextension.lb.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Import;

@LoadBalancerClients(defaultConfiguration = VersionLoadBalancerConfig.class)
@ConditionalOnProperty(prefix = "spring.gateway.lb.version.isolation", name = "enabled", havingValue = "true")
@Import({VersionRegisterBeanPostProcessor.class})
public class VersionIsolationAutoConfig {
}
