package com.scott.payment.job.config;

import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * service-job 调用 service-admin 内部接口的 HMAC-SHA256 客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "job.admin-client")
public class AdminInternalClientProperties {

    /** service-admin 建连超时，单位毫秒。 */
    private int connectTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /** service-admin 业务读取超时，单位毫秒。 */
    private int readTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_READ_TIMEOUT_MILLIS;

    /** 固定内部调用方标识。 */
    private String internalCaller = "service-job";

    /** Nacos 注入的 job-admin 调用边共享密钥。 */
    private String internalSecret;

    /**
     * 启动前校验调用方、密钥和超时范围。
     */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "job admin-client", "service-job", internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "job admin-client", connectTimeoutMillis, readTimeoutMillis);
    }
}
