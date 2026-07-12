package com.scott.payment.admin.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerClientConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度客户端配置类
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerClientConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Scheduler Client 配置，位于 service-admin 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(JobSchedulerClientProperties.class)
public class JobSchedulerClientConfig {

    /**
     * 任务调度内部直连超时时间。
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    /**
     * 任务调度内部直连读取超时时间。
     */
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    /**
     * 注册直连 RestTemplate。
     *
     * @return 直连 RestTemplate
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean("jobSchedulerRestTemplate")
    public RestTemplate jobSchedulerRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return new RestTemplate(requestFactory);
    }

    /**
     * 注册负载均衡 RestTemplate。
     *
     * @return 负载均衡 RestTemplate
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param restTemplateBuilder 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean("jobSchedulerLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate jobSchedulerLoadBalancedRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                .setReadTimeout(java.time.Duration.ofMillis(READ_TIMEOUT_MILLIS))
                .build();
    }
}
