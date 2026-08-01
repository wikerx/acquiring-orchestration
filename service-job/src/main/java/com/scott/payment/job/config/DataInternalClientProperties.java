package com.scott.payment.job.config;

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

    /** 内部调用方标识，用于 service-data 审计来源。 */
    private String internalCaller = "service-job";

    /** HMAC-SHA256 共享密钥，UAT 和生产必须由受控配置注入。 */
    private String internalSecret = "dev-internal-service-secret";
}
