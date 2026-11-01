package com.scott.payment.admin.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * service-admin 调用清分内部接口的地址、有界超时和 HMAC 身份配置；仅控制通信参数，不作为自动清分业务开关。
 */
@Data
@ConfigurationProperties(prefix = "admin.clearing-client")
public class ClearingInternalClientProperties {
    private static final String REQUIRED_CALLER = "service-admin";
    /**
     * service-clearing 的 HTTP(S) 服务根地址，不允许携带凭据、查询或片段。
     */
    private String baseUrl = "http://service-clearing";
    /**
     * 建连超时，单位毫秒，必须大于零。
     */
    private int connectTimeoutMillis = 3_000;
    /**
     * 内部管理调用读取超时，单位毫秒，必须大于零。
     */
    private int readTimeoutMillis = 30_000;
    /**
     * 内部调用身份，固定为 service-admin。
     */
    private String internalCaller = "service-admin";
    /**
     * HMAC 密钥，敏感字段，不允许为空或使用开发默认值。
     */
    private String internalSecret;

    /**
     * 启动前校验清分服务根地址、调用身份、HMAC 密钥和有界超时。
     */
    public void validate() {
        validateBaseUrl();
        InternalServiceClientCredentialValidator.validate(
                "admin clearing-client", REQUIRED_CALLER, internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "admin clearing-client", connectTimeoutMillis, readTimeoutMillis);
    }

    /**
     * 只允许无凭据、无查询和无片段的 HTTP(S) 服务根地址。
     */
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
