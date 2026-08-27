package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/** service-admin 调用清分内部管理接口的地址、超时和 HMAC 配置。 */
@Data
@ConfigurationProperties(prefix = "admin.clearing-client")
public class ClearingInternalClientProperties {
    private static final String REQUIRED_CALLER = "service-admin";
    private static final String DEVELOPMENT_SECRET = "dev-internal-service-secret";

    private String baseUrl = "http://service-clearing";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 30_000;
    private String internalCaller = "service-admin";
    private String internalSecret;

    /** 启动前校验清分服务根地址、调用身份、HMAC 密钥和有界超时。 */
    public void validate() {
        validateBaseUrl();
        if (!REQUIRED_CALLER.equals(internalCaller)) {
            throw new IllegalStateException("admin clearing-client caller must be service-admin");
        }
        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("admin clearing-client internal secret is required");
        }
        if (DEVELOPMENT_SECRET.equals(internalSecret.trim())) {
            throw new IllegalStateException("admin clearing-client development secret is not allowed");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalStateException("admin clearing-client timeouts must be positive");
        }
    }

    /** 只允许无凭据、无查询和无片段的 HTTP(S) 服务根地址。 */
    private void validateBaseUrl() {
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            String path = uri.getPath();
            if (!validScheme || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (StringUtils.hasText(path) && !"/".equals(path))) {
                throw new IllegalStateException("admin clearing-client base-url must be an HTTP(S) service root");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("admin clearing-client base-url is invalid", exception);
        }
    }
}
