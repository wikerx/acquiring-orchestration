package com.scott.payment.channel.payment.scottchannela;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAProperties
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 的环境级兜底配置。正式交易优先读取路由结果中的 MID 元数据，避免把商户凭据写入代码或默认配置。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.channel.scott-channel-a")
public class ScottChannelAProperties {

    /** 是否注册 ScottChannelA provider。 */
    private boolean enabled = true;

    /** 渠道 API 基础地址；MID 元数据存在时由 MID 覆盖。 */
    private String baseUrl;

    /** 渠道 API 版本；MID 元数据存在时由 MID 覆盖。 */
    private String version = "v1";

    /** 环境级回调验签密钥兜底值，禁止写入仓库。 */
    private String callbackSecret;

    /** 环境级 MID 兜底值。 */
    private String merchantId;

    /** 环境级 API Key 兜底值，禁止写入仓库。 */
    private String apiKey;

    /** 连接超时时间，单位毫秒。 */
    private int connectTimeoutMillis = 15000;

    /** 读取超时时间，单位毫秒。 */
    private int readTimeoutMillis = 15000;
}
