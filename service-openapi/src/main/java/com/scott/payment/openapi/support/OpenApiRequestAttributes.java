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

    /**
     * 请求上下文中的标准化请求头对象 key，值类型为 {@code OpenApiRequestHeaderDTO}。
     */
    public static final String REQUEST_HEADER = OpenApiRequestAttributes.class.getName() + ".REQUEST_HEADER";

    /**
     * 请求上下文中的解密后业务 DTO key，供方法参数解析器注入控制器参数。
     */
    public static final String DECRYPTED_DATA = OpenApiRequestAttributes.class.getName() + ".DECRYPTED_DATA";

    private OpenApiRequestAttributes() {
    }
}
