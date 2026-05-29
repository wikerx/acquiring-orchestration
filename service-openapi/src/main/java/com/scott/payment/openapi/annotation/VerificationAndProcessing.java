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

    /**
     * 解密后的业务 DTO 类型。
     * <p>
     * 控制器仍然可以接收原始密文字符串，同时由参数解析器把解密后的对象注入到同类型方法参数中。
     *
     * @return 解密后需要转换的目标 DTO 类型
     */
    Class<?> dataReceiver() default Void.class;

    /**
     * 是否开启 Bean Validation 属性校验。
     * <p>
     * 默认开启，适合对外 API 严格校验；内部联调或特殊回调接口可以按需关闭。
     *
     * @return true 表示执行属性校验，false 表示只完成验签和解密
     */
    boolean validator() default true;

    /**
     * Bean Validation 分组。
     * <p>
     * 同一个公共 DTO 可以通过不同分组服务授权、请款、退款、撤销等接口，避免重复定义对象。
     *
     * @return 校验分组类型数组
     */
    Class<?>[] validationGroups() default {};

    /**
     * 是否要求接口必须携带指定请求头。
     * <p>
     * 默认要求开放接口携带 authorization；健康检查、内部回调等无需商户 JWT 的接口可关闭。
     *
     * @return true 表示进入控制器前校验请求头，false 表示跳过请求头校验
     */
    boolean requiredHeader() default true;

    /**
     * 当前接口必须存在的请求头名称。
     * <p>
     * 默认只强制 authorization，后续如要增加租户、渠道或幂等头，可以在方法注解上覆盖。
     *
     * @return 请求头名称数组
     */
    String[] requiredHeaders() default {
            "authorization"
    };
}
