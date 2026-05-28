package com.scott.payment.component.web.version;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiVersion
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : REST API 版本标识注解
 * @status : create
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {

    /**
     * 声明控制器支持的 API 版本。
     *
     * @return API 版本号
     */
    int apiVersion() default 1;
}
