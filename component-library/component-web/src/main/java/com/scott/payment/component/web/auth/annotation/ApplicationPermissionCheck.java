package com.scott.payment.component.web.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明权限由应用服务根据请求中的业务范围校验。统一拦截器仍校验登录态和 CSRF，
 * 显式接口权限优先，应用服务必须在读取数据或执行操作之前完成范围授权。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplicationPermissionCheck {
}
