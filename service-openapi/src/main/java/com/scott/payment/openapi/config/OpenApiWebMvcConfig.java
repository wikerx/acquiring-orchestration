package com.scott.payment.openapi.config;

import com.scott.payment.openapi.support.OpenApiHeaderInterceptor;
import com.scott.payment.openapi.support.OpenApiRequestArgumentResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;


@Configuration
@EnableConfigurationProperties({OpenApiCallbackProperties.class, OpenApiMerchantSecretCacheProperties.class})
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiWebMvcConfig
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Web Mvc Config 配置类，位于 商户开放接口服务，注册当前模块运行所需 Bean、拦截器、客户端或配置属性。
 * @status : create
 */
public class OpenApiWebMvcConfig implements WebMvcConfigurer {

    /**
     * 开放 API 请求头拦截器，进入控制器前完成 Authorization/JWT 校验。
     */
    private final OpenApiHeaderInterceptor headerInterceptor;

    /**
     * 开放 API 参数解析器，用于把解密后的 DTO 注入控制器方法参数。
     */
    private final OpenApiRequestArgumentResolver requestArgumentResolver;

    /**
     * 创建开放接口 Web MVC 配置。
     *
     * @param headerInterceptor      请求头拦截器
     * @param requestArgumentResolver 参数解析器
     */
    public OpenApiWebMvcConfig(OpenApiHeaderInterceptor headerInterceptor,
                               OpenApiRequestArgumentResolver requestArgumentResolver) {
        this.headerInterceptor = headerInterceptor;
        this.requestArgumentResolver = requestArgumentResolver;
    }

    /**
     * 注册开放 API 请求头拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(headerInterceptor).addPathPatterns("/openapi/**", "/channel/**", "/api/rest/**");
    }

    /**
     * 注册开放 API 解密 DTO 参数解析器。
     *
     * @param resolvers 参数解析器列表
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(requestArgumentResolver);
    }
}
