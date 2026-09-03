package com.scott.payment.admin.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalClientProperties
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : service-admin 调用结算管理接口的服务根地址、有界超时和 HMAC 调用身份。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "admin.settlement-client")
public class SettlementInternalClientProperties {

    /** service-settlement 的 HTTP(S) 服务根地址，不得包含凭据、查询参数或业务路径。 */
    private String baseUrl = "http://service-settlement";
    /** 内部 HTTP 建连超时，单位毫秒；仅约束远程调用，不是自动结算业务开关。 */
    private int connectTimeoutMillis = 3_000;
    /** 内部 HTTP 读取超时，单位毫秒；仅约束远程调用，不是自动结算业务开关。 */
    private int readTimeoutMillis = 30_000;
    /** 内部 HMAC 固定调用方身份，只允许 service-admin。 */
    private String internalCaller = "service-admin";
    /** 内部 HMAC 共享密钥，属于敏感配置，禁止写入日志或返回浏览器。 */
    private String internalSecret;

    /** 校验根地址不含凭据、查询和片段，并校验固定调用身份与有界超时。 */
    public void validate() {
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
            String path = uri.getPath();
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!validScheme || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (StringUtils.hasText(path) && !"/".equals(path))) {
                throw new IllegalStateException("admin settlement-client base-url must be an HTTP(S) service root");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("admin settlement-client base-url is invalid", exception);
        }
        InternalServiceClientCredentialValidator.validate(
                "admin settlement-client", "service-admin", internalCaller, internalSecret);
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalStateException("admin settlement-client timeouts must be positive");
        }
    }
}
