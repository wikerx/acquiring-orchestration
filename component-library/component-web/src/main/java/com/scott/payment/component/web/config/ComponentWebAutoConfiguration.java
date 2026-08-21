package com.scott.payment.component.web.config;

import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.component.web.handler.UnifiedErrorController;
import com.scott.payment.component.web.internal.InternalServiceRequestBodyFilter;
import com.scott.payment.component.web.trace.HttpTrafficLoggingFilter;
import com.scott.payment.component.web.trace.TraceIdFilter;
import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import com.scott.payment.component.web.version.ApiVersionWebMvcRegistrations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ComponentWebAutoConfiguration
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Web 组件自动装配配置
 * @status : create
 */
@Configuration
public class ComponentWebAutoConfiguration {

    /**
     * 注册 Fastjson2 MVC 配置。
     *
     * @return Fastjson2 MVC 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public FastJsonWebMvcConfig fastJsonWebMvcConfig() {
        return new FastJsonWebMvcConfig();
    }

    /**
     * 注册全局异常处理器。
     *
     * @return 全局异常处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 注册统一兜底错误控制器。
     *
     * @return 统一错误控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public UnifiedErrorController unifiedErrorController() {
        return new UnifiedErrorController();
    }

    /**
     * 注册 Servlet traceId 过滤器。
     *
     * @return traceId 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /**
     * 注册 HTTP 请求响应摘要日志过滤器。
     *
     * @return HTTP traffic 日志过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpTrafficLoggingFilter httpTrafficLoggingFilter() {
        return new HttpTrafficLoggingFilter();
    }

    /**
     * 注册内部服务请求体摘要与回放过滤器。
     *
     * @return 内部请求体过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalServiceRequestBodyFilter internalServiceRequestBodyFilter() {
        return new InternalServiceRequestBodyFilter();
    }

    /**
     * 注册 RestTemplate traceId 拦截器。
     *
     * @return traceId 请求头拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdRestTemplateInterceptor traceIdRestTemplateInterceptor() {
        return new TraceIdRestTemplateInterceptor();
    }

    /**
     * 注册 RestTemplate traceId 定制器。
     *
     * @param interceptor traceId 请求头拦截器
     * @return RestTemplate 定制器
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer(TraceIdRestTemplateInterceptor interceptor) {
        return new TraceIdRestTemplateCustomizer(interceptor);
    }

    /**
     * 注册 API 版本路由映射。
     *
     * @return Web MVC 注册器
     */
    @Bean
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public ApiVersionWebMvcRegistrations apiVersionWebMvcRegistrations() {
        return new ApiVersionWebMvcRegistrations();
    }
}
