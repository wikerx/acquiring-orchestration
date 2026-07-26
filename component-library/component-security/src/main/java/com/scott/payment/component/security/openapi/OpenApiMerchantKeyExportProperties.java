package com.scott.payment.component.security.openapi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "acquiring.openapi.merchant-key-export")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantKeyExportProperties
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Merchant Key Export Properties 配置属性模型，位于 公共组件库，绑定 application 配置项并提供运行时默认值。
 * @status : create
 */
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
