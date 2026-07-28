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
@Configuration
public class AdminAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * system Auth Service 依赖，用于 Admin Auth Web Mvc Config 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
                "/admin/auth/mfa/bind-info",
                "/admin/auth/mfa/bind-confirm",
                "/admin/auth/mfa/verify",
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
