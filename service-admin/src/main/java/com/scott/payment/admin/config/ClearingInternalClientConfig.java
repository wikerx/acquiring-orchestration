package com.scott.payment.admin.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;
import java.time.Duration;

/**
 * 清分管理内部客户端的直连和服务发现 HTTP 配置。
 */
@Configuration
@EnableConfigurationProperties(ClearingInternalClientProperties.class)
public class ClearingInternalClientConfig {

    /**
     * 构造用于固定地址访问的无代理内部 HTTP 客户端。
     * @param customizer 框架定制器，用于为客户端补充 traceId、超时或其它统一调用约束
     * @param properties 已绑定并校验的运行时配置，提供服务地址、调用身份和有界超时
     * @return 当前方法生成的 {@code RestTemplate} 结果
     */
    @Bean("adminClearingInternalRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               ClearingInternalClientProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    /**
     * 构造通过服务发现解析目标实例的负载均衡内部 HTTP 客户端。
     * <p>
     * 仅创建客户端 Bean，不发起远程调用或读取业务数据。
     * </p>
     * @param builder 框架构建器，用于按当前配置创建客户端、请求对象或运行时组件
     * @param interceptor 请求拦截器，用于透传链路标识或执行当前调用边界的统一处理
     * @param properties 已绑定并校验的运行时配置，提供服务地址、调用身份和有界超时
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Bean("adminClearingInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     ClearingInternalClientProperties properties) {
        validate(properties);
        return builder.connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor).build();
    }

    /**
     * 校验输入是否满足当前业务约束。
     */
    private void validate(ClearingInternalClientProperties properties) {
        properties.validate();
    }
}
