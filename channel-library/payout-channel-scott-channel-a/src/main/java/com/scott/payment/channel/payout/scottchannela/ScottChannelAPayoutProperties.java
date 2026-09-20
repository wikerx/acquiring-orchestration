package com.scott.payment.channel.payout.scottchannela;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPayoutProperties
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 代付 API 的环境级兜底配置；正式调用优先读取代付路由传入的 MID 元数据。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.channel.scott-channel-a.payout")
public class ScottChannelAPayoutProperties {

    /** 是否注册 ScottChannelA 代付 Provider。 */
    private boolean enabled = true;

    /** 渠道 API 基础地址。 */
    private String baseUrl;

    /** 渠道 API 版本。 */
    private String version = "v1";

    /** 环境级 MID 兜底值。 */
    private String merchantId;

    /** 环境级 API Key 兜底值，禁止写入仓库。 */
    private String apiKey;

    /** 连接超时时间，单位毫秒。 */
    private int connectTimeoutMillis = 15000;

    /** 读取超时时间，单位毫秒。 */
    private int readTimeoutMillis = 15000;
}
