package com.scott.payment.admin.config;

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

    private String baseUrl = "http://service-settlement";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 30_000;
    private String internalCaller = "service-admin";
    private String internalSecret = "dev-internal-service-secret";

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
        if (!"service-admin".equals(internalCaller) || !StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("admin settlement-client HMAC identity is invalid");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalStateException("admin settlement-client timeouts must be positive");
        }
    }
}
