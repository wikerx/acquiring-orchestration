package com.scott.payment.admin.config;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminAuthWebMvcConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台认证WebMvc配置类
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminAuthWebMvcConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Auth Web Mvc 配置，位于 service-admin 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
public class AdminAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SystemAuthService systemAuthService;

    /**
     * 创建管理后台鉴权拦截配置。
     *
     * @param systemAuthService 系统鉴权服务
     */
    public AdminAuthWebMvcConfig(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
    }

    /**
     * 为管理后台接口注册统一鉴权拦截器。
     *
     * @param registry 拦截器注册器
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param registry 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(AuthConstants.APP_ADMIN, systemAuthService, whitelist()))
                .addPathPatterns("/admin/**");
    }

    /**
     * 定义无需登录即可访问的后台白名单路径。
     *
     * @return 白名单路径集合
     */
    private List<String> whitelist() {
        return List.of(
                "/admin/auth/login",
                "/admin/auth/verify-code/send",
                "/admin/health/**",
                "/actuator/health/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/error"
        );
    }
}
