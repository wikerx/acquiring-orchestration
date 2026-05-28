package com.scott.payment.openapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : VerificationAndProcessing
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口验签与处理标识注解
 * @status : create
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VerificationAndProcessing {

    Class<?> dataReceiver() default Void.class;

    boolean validator() default true;

    Class<?>[] validationGroups() default {};

    boolean requiredHeader() default true;

    String[] requiredHeaders() default {
            "authorization"
    };
}
