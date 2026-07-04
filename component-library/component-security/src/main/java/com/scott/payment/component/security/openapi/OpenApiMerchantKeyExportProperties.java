package com.scott.payment.component.security.openapi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantKeyExportProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Merchant Key Export 配置属性，位于 component-library/component-security 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.openapi.merchant-key-export")
public class OpenApiMerchantKeyExportProperties {

    /**
     * 商户调用 OpenAPI 的基础地址，会写入导出的 merchant-config.properties。
     */
    private String openApiBaseUrl;

    /**
     * 对外推荐使用的 Java SDK 版本，仅用于页面展示和接入包说明。
     */
    private String sdkVersion;

    /**
     * 商户侧接入包展示的加密模式说明。
     */
    private String cryptoMode;
}
