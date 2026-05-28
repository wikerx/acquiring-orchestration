package com.scott.payment.openapi.support;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestAttributes
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口请求属性常量
 * @status : create
 */
public final class OpenApiRequestAttributes {

    public static final String REQUEST_HEADER = OpenApiRequestAttributes.class.getName() + ".REQUEST_HEADER";
    public static final String DECRYPTED_DATA = OpenApiRequestAttributes.class.getName() + ".DECRYPTED_DATA";

    private OpenApiRequestAttributes() {
    }
}
