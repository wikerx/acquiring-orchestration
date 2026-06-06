package com.scott.payment.component.web.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RequiresPermission
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理接口权限标记
 * @status : create
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 权限编码。
     *
     * @return 权限编码
     */
    String value();
}
