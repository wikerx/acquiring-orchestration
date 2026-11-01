package com.scott.payment.job.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * service-job 调用清分补偿内部接口的地址、有界超时和 HMAC 身份配置；不作为自动清分业务开关。
 */
@Data
@ConfigurationProperties(prefix = "job.clearing-client")
public class ClearingInternalClientProperties {
    /**
     * 清分内部接口签名要求的固定调用方身份。
     */
    private static final String REQUIRED_CALLER = "service-job";
    /**
     * service-clearing 的 HTTP(S) 服务根地址，不允许携带凭据、查询或片段。
     */
    private String baseUrl = "http://service-clearing";
    /**
     * 建连超时，单位毫秒，必须大于零。
     */
    private int connectTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;
    /**
     * 单页补偿扫描读取超时，单位毫秒，必须大于零。
     */
    private int readTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_READ_TIMEOUT_MILLIS;
    /**
     * 内部调用身份，固定为 service-job。
     */
    private String internalCaller = "service-job";
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
                "job clearing-client", REQUIRED_CALLER, internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "job clearing-client", connectTimeoutMillis, readTimeoutMillis);
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
                throw new IllegalStateException("job clearing-client base-url must be an HTTP(S) service root");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("job clearing-client base-url is invalid", exception);
        }
    }
}
