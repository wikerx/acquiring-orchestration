package com.sinopay.payment.component.web.config;

import com.sinopay.payment.component.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    @Bean
    @ConditionalOnMissingBean
    public FastJsonWebMvcConfig fastJsonWebMvcConfig() {
        return new FastJsonWebMvcConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
