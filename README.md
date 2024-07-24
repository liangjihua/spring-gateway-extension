# spring-gateway-extension

> 此仓库仅用于问题及解决方案代码示例和参考，并没有发布包到 maven 仓库中，也不建议直接 clone 到本地 install。

## 问题

使用 spring cloud 作为企业的基础框架，会遇到一个非常常见且基本的问题，该问题很多企业仍然没有意识到，或者没有被解决:

在一个共享的开发环境之中，前端 - 后端联调过程中，如何指定特定的后端实例？

这个问题非常常见，但是 spring cloud 的文档中并未提及这一点，框架中也未集成默认的解决方案。

该问题的核心点在于：

1. 对特定请求进行**标记**
2. 对服务实例进行**标记**
3. 在对请求进行路由时，根据该**标记**将请求路由到符合的后端实例
4. 在多个开发人员之间协调该**标记**

## 解决方案

我们可以通过扩展 maven + nacos 来解决 2 和 4。
我们在 [pom.xml](pom.xml) 中增加一组插件配置，该配置将当前 git 用户名的信息写入到 user home 中，
另外我们定制了服务实例在 nacos 注册的过程，在注册时将前面写入到 user home 中的用户名添加到
服务实例的 metadata 中。
代码：[VersionRegisterBeanPostProcessor.java](src%2Fmain%2Fjava%2Fcn%2Fliangjihua%2Fspringgatewayextension%2Flb%2Fconfig%2FVersionRegisterBeanPostProcessor.java)

我们通过扩展 spring cloud loadbalancer 来解决 1 和 3。
loadbalancer 是最终负责选取实例的组件，
我们通过在请求头中添加一个特殊的请求头，其值为我们正在与之联调的开发人员的 git 用户名，
loadbalancer 根据该请求头的值来选取符合的实例。
代码：[VersionLoadBalancer.java](src%2Fmain%2Fjava%2Fcn%2Fliangjihua%2Fspringgatewayextension%2Flb%2FVersionLoadBalancer.java)

示例：

```http request
POST /api/user/login HTTP/1.1
X-service-version: wangxiaohu

```

loadbalancer 会读取 `X-service-version` , 并将其路由到 `wangxiaohu` (王小虎)正在开发的服务实例。

## 负载均衡器优化

spring cloud loadbalancer 默认对于服务实例信息会进行缓存，
这个缓存的时间默认为 35 秒 查看：`org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheProperties#getTtl`
这会导致在服务实例上下线的时候，spring cloud gateway 不能及时的获取到最新的服务实例列表，
可能会将请求转发至已经下线的服务实例中，导致请求失败。

在开发过程当中，服务实例上下线的情况非常频繁，这种策略非常影响开发体验。
因此我们对 spring cloud loadbalancer 进行了优化，这种优化是特定于 `nacos` 的，因此如果你的注册中心
使用的不是 `nacos`，那么此优化并不适合。

解决方案思路如下：

1. 通过自定义 nacos 的消息监听器，监听 nacos 推送的服务上下线事件
2. 根据服务上下线事件，立即主动驱逐 spring cloud loadbalancer 的服务实例缓存

具体代码可以查看[ServiceInstanceCacheRefresh.java](src%2Fmain%2Fjava%2Fcn%2Fliangjihua%2Fspringgatewayextension%2Flb%2FServiceInstanceCacheRefresh.java)