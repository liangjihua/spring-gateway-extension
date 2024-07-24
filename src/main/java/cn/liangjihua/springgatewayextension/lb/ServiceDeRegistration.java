package cn.liangjihua.springgatewayextension.lb;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.SmartLifecycle;

/**
 * 扩展 spring lifecycle, 在应用关闭前，向注册中心取消服务注册，并等待15秒种以确保本服务实例下线的信息同步至其他服务，
 * 在等待期间，应用实例仍然可以正常响应请求.
 * <p></p>
 * 默认等待 15 秒钟是因为 nacos 客户端对于服务实例信息的默认轮询时间是 10 秒钟.
 */
@Slf4j
public class ServiceDeRegistration implements SmartLifecycle {
    public static final long DEFAULT_WAIT_MILLIS = 15000L;

    private final ServiceRegistry<Registration> serviceRegistry;

    private final Registration registration;
    private final long waitMillis;

    public ServiceDeRegistration(ServiceRegistry<Registration> serviceRegistry,
                                 Registration registration) {
        this(serviceRegistry, registration, DEFAULT_WAIT_MILLIS);
    }

    public ServiceDeRegistration(ServiceRegistry<Registration> serviceRegistry,
                                 Registration registration,
                                 long waitMillis) {
        this.serviceRegistry = serviceRegistry;
        this.registration = registration;
        this.waitMillis = waitMillis;
    }

    @Override
    public void start() {
    }

    @SneakyThrows
    @Override
    public void stop() {
        log.debug("取消服务注册...");
        serviceRegistry.deregister(registration);
        Thread.sleep(waitMillis);
        log.debug("取消服务注册完成..");
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.super.getPhase();
    }
}
