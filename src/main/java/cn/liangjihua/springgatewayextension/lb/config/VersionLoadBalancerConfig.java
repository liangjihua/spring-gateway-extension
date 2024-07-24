package cn.liangjihua.springgatewayextension.lb.config;

import cn.liangjihua.springgatewayextension.lb.VersionLoadBalancer;
import cn.liangjihua.springgatewayextension.lb.VersionLoadBalancerLifecycle;
import cn.liangjihua.springgatewayextension.lb.chooser.IRuleChooser;
import cn.liangjihua.springgatewayextension.lb.chooser.RoundRuleChooser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;


/**
 * 版本控制的路由选择类配置
 */
@Slf4j
public class VersionLoadBalancerConfig {

    @Bean
    @ConditionalOnMissingBean(IRuleChooser.class)
    @ConditionalOnProperty(prefix = "spring.gateway.lb.version.isolation", value = "chooser")
    public IRuleChooser customRuleChooser(Environment environment,
                                          ApplicationContext context,
                                          @Value("${spring.gateway.lb.version.isolation.chooser}") String chooserClass)
            throws NoSuchMethodException, InvocationTargetException {

        try {
            Class<?> ruleClass = ClassUtils.forName(chooserClass, context.getClassLoader());
            return (IRuleChooser) ruleClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            log.error("没有找到指定的 lb 的选择器，将使用默认的选择器", e);
        } catch (InstantiationException | IllegalAccessException e) {
            log.error("无法创建指定的 lb 的选择器，将使用默认的选择器", e);
        }
        return new RoundRuleChooser();
    }

    @Bean
    @ConditionalOnMissingBean(value = IRuleChooser.class)
    public IRuleChooser defaultRuleChooser() {
        return new RoundRuleChooser();
    }


    @Bean
    @ConditionalOnProperty(prefix = "spring.gateway.lb.version.isolation", name = "enabled", havingValue = "true")
    public ReactorServiceInstanceLoadBalancer versionServiceLoadBalancer(Environment environment,
                                                                         LoadBalancerClientFactory factory,
                                                                         IRuleChooser ruleChooser) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new VersionLoadBalancer(factory.getLazyProvider(name, ServiceInstanceListSupplier.class),
            name, ruleChooser);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.gateway.lb.version.isolation", name = "enabled", havingValue = "true")
    public LoadBalancerLifecycle<RequestDataContext, ResponseData, ServiceInstance> loadBalancerLifecycle() {
        return new VersionLoadBalancerLifecycle();
    }
}
