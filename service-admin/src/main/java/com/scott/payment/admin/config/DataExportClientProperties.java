package com.scott.payment.admin.config;

import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Admin 调用 Data 异步导出服务的地址、超时和独立 HMAC 身份配置。
 */
@Data
@ConfigurationProperties(prefix = "admin.data-client")
public class DataExportClientProperties {
    private String baseUrl = "http://service-data";
    private int connectTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int readTimeoutMillis = 60_000;
    private String internalCaller = "service-admin";
    private String internalSecret;

    /**
     * 校验绝对服务地址、有界超时和独立服务身份，禁止回退浏览器凭据。
     */
    public void validate() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("admin.data-client.base-url is required");
        }
        URI uri = URI.create(baseUrl.trim());
        if (uri.getScheme() == null || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("admin.data-client.base-url must be an absolute HTTP URL");
        }
        InternalServiceClientCredentialValidator.validate(
                "admin data-client", "service-admin", internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "admin data-client", connectTimeoutMillis, readTimeoutMillis);
    }
}
