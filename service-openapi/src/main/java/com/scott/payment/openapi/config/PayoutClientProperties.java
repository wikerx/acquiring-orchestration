package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI 调用 service-payout 的客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "openapi.payout-client")
public class PayoutClientProperties {

    /**
     * 是否启用远程 service-payout 调用。
     */
    private boolean remoteEnabled = true;

    /**
     * service-payout 内部创建接口地址。
     */
    private String createUrl = "http://service-payout/internal/payout/create";
}
