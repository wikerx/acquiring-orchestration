package com.scott.payment.data.config.export;

import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * service-data 回调 Admin 导出源接口的地址、超时和 HMAC 身份配置。
 */
@Data
@ConfigurationProperties(prefix = "data.admin-client")
public class DataAdminClientProperties {
    private String baseUrl = "http://service-admin";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 30_000;
    private String internalCaller = "service-data";
    private String internalSecret;

    /**
     * 校验独立调用身份和有界超时，禁止复用浏览器凭据。
     */
    public void validate() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("data.admin-client.base-url is required");
        }
        URI uri = URI.create(baseUrl.trim());
        if (uri.getScheme() == null || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("data.admin-client.base-url must be an absolute HTTP URL");
        }
        InternalServiceClientCredentialValidator.validate(
                "data admin-client", "service-data", internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "data admin-client", connectTimeoutMillis, readTimeoutMillis);
    }
}
