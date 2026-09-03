package com.scott.payment.job.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataInternalClientProperties
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-job 调用 service-data 内部补偿接口的 HMAC-SHA256 客户端配置
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "job.data-client")
public class DataInternalClientProperties {

    /** service-data 建连超时，单位毫秒。 */
    private int connectTimeoutMillis = 3_000;

    /** service-data 有界批次读取超时，单位毫秒。 */
    private int readTimeoutMillis = 240_000;

    /** 内部调用方标识，用于 service-data 审计来源。 */
    private String internalCaller = "service-job";

    /** HMAC-SHA256 共享密钥，UAT 和生产必须由受控配置注入。 */
    private String internalSecret;

    /** 启动前校验固定调用方和 Nacos 注入的 active 密钥。 */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "job data-client", "service-job", internalCaller, internalSecret);
    }
}
