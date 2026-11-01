package com.scott.payment.channel.payment.worldpay.support;

/**
 * Worldpay provider 渠道编码常量，位于 payment-channel-worldpay 实现层，隔离 XML 与 JSON 两种注册实现。
 */
public final class ChannelCode {

    /**
     * Worldpay Gateway XML 实现在 Registry 和渠道配置中的稳定编码。
     */
    public static final String WPGXML = "WPGXML";

    /**
     * Worldpay Gateway JSON 实现在 Registry 和渠道配置中的稳定编码。
     */
    public static final String WPGJSON = "WPGJSON";

    /**
     * 禁止实例化渠道编码常量类。
     */
    private ChannelCode() {
    }
}
