package com.scott.payment.job.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingInternalClientProperties
 * @date : 2026-09-01 22:30
 * @email : scott_x@163.com
 * @description : service-job 调用清分补偿内部接口的地址、有界超时和 HMAC 身份配置；不作为自动清分业务开关。
 * @status : update
 */
@Data
@ConfigurationProperties(prefix = "job.clearing-client")
public class ClearingInternalClientProperties {
    /**
     * {@code REQUIRED_CALLER}常量，统一 {@code ClearingInternalClientProperties} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String REQUIRED_CALLER = "service-job";
    /** service-clearing 的 HTTP(S) 服务根地址，不允许携带凭据、查询或片段。 */
    private String baseUrl = "http://service-clearing";
    /** 建连超时，单位毫秒，必须大于零。 */
    private int connectTimeoutMillis = 3_000;
    /** 单页补偿扫描读取超时，单位毫秒，必须大于零。 */
    private int readTimeoutMillis = 120_000;
    /** 内部调用身份，固定为 service-job。 */
    private String internalCaller = "service-job";
    /** HMAC 密钥，敏感字段，不允许为空或使用开发默认值。 */
    private String internalSecret;

    /** 启动前校验清分服务根地址、调用身份、HMAC 密钥和有界超时。 */
    public void validate() {
        validateBaseUrl();
        InternalServiceClientCredentialValidator.validate(
                "job clearing-client", REQUIRED_CALLER, internalCaller, internalSecret);
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
