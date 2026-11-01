package com.scott.payment.component.web.trace;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * RestTemplate traceId 定制器，为手工创建的 HTTP 客户端统一追加 X-Trace-Id 传播拦截器。
 */
public class TraceIdRestTemplateCustomizer {


    private final TraceIdRestTemplateInterceptor interceptor;

    /**
     * 创建使用统一 traceId 传播拦截器的 RestTemplate 定制器。
     *
     * @param interceptor traceId 请求头传播拦截器
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
