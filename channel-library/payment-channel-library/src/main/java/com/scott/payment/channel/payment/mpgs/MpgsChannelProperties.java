package com.scott.payment.channel.payment.mpgs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsChannelProperties
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道兜底属性，位于 payment-channel-library 渠道配置层；交易优先使用数据库 MID 路由结果，禁止在代码中硬编码渠道密码。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.channel.mpgs")
public class MpgsChannelProperties {

    /**
     * MPGS REST 基础地址，例如 https://test-gateway.mastercard.com/api/rest。
     */
    private String baseUrl;

    /**
     * MPGS API 版本号，例如 74、83、100。
     */
    private String version = "100";

    /**
     * MPGS 商户号。
     */
    private String merchantId;

    /**
     * MPGS Basic Auth 用户名，通常为 merchant.{merchantId}。
     */
    private String apiUsername;

    /**
     * MPGS Basic Auth 密码或商户密钥，必须来自安全配置。
     */
    private String apiPassword;

    /**
     * 连接超时时间，单位毫秒。
     */
    private int connectTimeoutMillis = 10000;

    /**
     * 读取超时时间，单位毫秒。
     */
    private int readTimeoutMillis = 30000;
}
