package cn.liangjihua.springgatewayextension.lb.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.annotation.Nonnull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static cn.liangjihua.springgatewayextension.lb.constants.LbConstants.SERVICE_META_VERSION_KEY;


/**
 * 将版本注册到注册中心的组件
 */
public class VersionRegisterBeanPostProcessor implements BeanPostProcessor {
    private final String version;
    private static final String DEVELOPER_FILE = String.format("%s/developer", System.getProperty("user.home"));

    public VersionRegisterBeanPostProcessor() throws IOException {
        Path developerFilePath = Paths.get(DEVELOPER_FILE);
        this.version = String.join("", Files.readAllLines(developerFilePath));
    }

    @Override
    public Object postProcessBeforeInitialization(@Nonnull Object bean,
                                                  @Nonnull String beanName) throws BeansException {
        if (bean instanceof NacosDiscoveryProperties nacosDiscoveryProperties && StringUtils.isNotBlank(version)) {
            nacosDiscoveryProperties.getMetadata().putIfAbsent(SERVICE_META_VERSION_KEY, version);
        }
        return bean;
    }
}
