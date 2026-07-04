package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutClientProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayout Client 配置属性，位于 service-openapi 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
