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

    /**
     * 请求进入 OpenAPI 安全拦截器的纳秒时间，用于统一计算端到端耗时。
     */
    public static final String REQUEST_START_NANOS = OpenApiRequestAttributes.class.getName() + ".REQUEST_START_NANOS";

    /**
     * 当前 OpenAPI 路径解析出的版本号，例如 v1。
     */
    public static final String API_VERSION = OpenApiRequestAttributes.class.getName() + ".API_VERSION";

    /**
     * 当前 OpenAPI 接口类型，例如 payment、payout、iso 或 channel-callback。
     */
    public static final String INTERFACE_TYPE = OpenApiRequestAttributes.class.getName() + ".INTERFACE_TYPE";

    /**
     * 控制器返回的业务码，响应加密处理器会在写出前回填。
     */
    public static final String BUSINESS_CODE = OpenApiRequestAttributes.class.getName() + ".BUSINESS_CODE";

    /**
     * 商户请求密文摘要 key，用于响应结束日志关联同一次请求的密文长度、指纹和 compact 结构。
     */
    public static final String REQUEST_CIPHER_SUMMARY = OpenApiRequestAttributes.class.getName() + ".REQUEST_CIPHER_SUMMARY";

    /**
     * 商户请求明文脱敏摘要 key，解密成功后写入，响应结束日志可用于一次性展示排障上下文。
     */
    public static final String REQUEST_PLAIN_SUMMARY = OpenApiRequestAttributes.class.getName() + ".REQUEST_PLAIN_SUMMARY";

    /**
     * 响应明文脱敏摘要 key，响应加密前写入，用于和响应密文摘要配对排查。
     */
    public static final String RESPONSE_PLAIN_SUMMARY = OpenApiRequestAttributes.class.getName() + ".RESPONSE_PLAIN_SUMMARY";

    /**
     * 响应密文摘要 key，响应加密后写入，用于和商户实际收到的 data 指纹比对。
     */
    public static final String RESPONSE_CIPHER_SUMMARY = OpenApiRequestAttributes.class.getName() + ".RESPONSE_CIPHER_SUMMARY";

    /**
     * OpenAPI 请求处理异常类型 key，异常诊断处理器写入，结束日志读取后统一输出。
     */
    public static final String EXCEPTION_TYPE = OpenApiRequestAttributes.class.getName() + ".EXCEPTION_TYPE";

    private OpenApiRequestAttributes() {
    }
}
