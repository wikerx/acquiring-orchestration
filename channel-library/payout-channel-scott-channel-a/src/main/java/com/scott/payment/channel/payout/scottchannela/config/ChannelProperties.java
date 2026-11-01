package com.scott.payment.channel.payout.scottchannela.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ScottChannelA 代付环境级连接兜底配置；正式交易优先使用路由传入的 MID 元数据。
 **/
@Data
@ConfigurationProperties(prefix = "payment.channel.scott-channel-a.payout")
public class ChannelProperties {

    /**
     * 是否注册 ScottChannelA 代付 Provider。
     **/
    private boolean enabled = true;

    /**
     * 渠道 API 基础地址，仅在渠道默认请求地址未传入时兜底。
     **/
    private String baseUrl;

    /**
     * 渠道 API 版本。
     **/
    private String version = "v1";

    /**
     * 环境级 MID 兜底值。
     **/
    private String merchantId;

    /**
     * 环境级 API Key 兜底值，禁止写入仓库。
     **/
    private String apiKey;

    /**
     * 连接超时时间，单位毫秒。
     **/
    private int connectTimeoutMillis = 15000;

    /**
     * 读取超时时间，单位毫秒。
     **/
    private int readTimeoutMillis = 15000;
}
