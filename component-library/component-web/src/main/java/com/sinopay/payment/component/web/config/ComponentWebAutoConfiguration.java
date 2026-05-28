package com.sinopay.payment.component.web.config;

import com.sinopay.payment.component.web.handler.GlobalExceptionHandler;
import com.sinopay.payment.component.web.version.ApiVersionWebMvcRegistrations;
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
