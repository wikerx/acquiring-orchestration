package com.scott.payment.channel.payment.mpgs.support;

/**
 * MPGS provider 渠道编码常量，位于 payment-channel-mpgs 实现层，避免公共渠道 API 枚举具体 PSP。
 */
public final class ChannelCode {

    /**
     * MPGS provider 在 Registry 和渠道配置中的稳定编码。
     */
    public static final String MPGS = "MPGS";

    /**
     * 禁止实例化渠道编码常量类。
     */
    private ChannelCode() {
    }
}
