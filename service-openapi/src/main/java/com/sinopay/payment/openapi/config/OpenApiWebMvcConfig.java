package com.sinopay.payment.openapi.config;

import com.sinopay.payment.openapi.support.OpenApiHeaderInterceptor;
import com.sinopay.payment.openapi.support.OpenApiRequestArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiWebMvcConfig
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口 Web MVC 扩展配置
 * @status : create
 */
@Configuration
public class OpenApiWebMvcConfig implements WebMvcConfigurer {

    private final OpenApiHeaderInterceptor headerInterceptor;
    private final OpenApiRequestArgumentResolver requestArgumentResolver;

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
