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
     * {@code interceptor} 依赖，用于 {@code TraceIdRestTemplateCustomizer} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final TraceIdRestTemplateInterceptor interceptor;

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
