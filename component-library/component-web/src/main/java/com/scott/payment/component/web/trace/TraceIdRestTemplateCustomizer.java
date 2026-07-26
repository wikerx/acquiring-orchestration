package com.scott.payment.component.web.trace;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdRestTemplateCustomizer
 * @date : 2026-07-26 15:30
 * @email : scott_x@163.com
 * @description : RestTemplate traceId 定制器，为手工创建的 HTTP 客户端统一追加 X-Trace-Id 传播拦截器。
 * @status : create
 */
public class TraceIdRestTemplateCustomizer {

    /**
     * interceptor 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TraceIdRestTemplateInterceptor interceptor;

    /**
     * 创建 TraceIdRestTemplateCustomizer 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 TraceIdRestTemplateCustomizer 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param interceptor interceptor 输入值，含义由调用方法名称和所属业务对象限定
     */
    public TraceIdRestTemplateCustomizer(TraceIdRestTemplateInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * 将 traceId 拦截器追加到 RestTemplate。
     *
     * @param restTemplate 待增强的 RestTemplate
     * @return 已追加 traceId 拦截器的 RestTemplate
     */
    public RestTemplate customize(RestTemplate restTemplate) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add(interceptor);
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
