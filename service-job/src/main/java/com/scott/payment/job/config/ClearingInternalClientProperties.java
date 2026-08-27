package com.scott.payment.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/** service-job 调用清分补偿内部接口的有界超时和 HMAC 配置。 */
@Data
@ConfigurationProperties(prefix = "job.clearing-client")
public class ClearingInternalClientProperties {
    private static final String REQUIRED_CALLER = "service-job";
    private static final String DEVELOPMENT_SECRET = "dev-internal-service-secret";

    private String baseUrl = "http://service-clearing";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 120_000;
    private String internalCaller = "service-job";
    private String internalSecret;

    /** 启动前校验清分服务根地址、调用身份、HMAC 密钥和有界超时。 */
    public void validate() {
        validateBaseUrl();
        if (!REQUIRED_CALLER.equals(internalCaller)) {
            throw new IllegalStateException("job clearing-client caller must be service-job");
        }
        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("job clearing-client internal secret is required");
        }
        if (DEVELOPMENT_SECRET.equals(internalSecret.trim())) {
            throw new IllegalStateException("job clearing-client development secret is not allowed");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalStateException("job clearing-client timeouts must be positive");
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
                throw new IllegalStateException("job clearing-client base-url must be an HTTP(S) service root");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("job clearing-client base-url is invalid", exception);
        }
    }
}
