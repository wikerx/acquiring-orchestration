package com.scott.payment.channel.payment.worldpay;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayChannelCode
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : Worldpay provider 渠道编码常量，位于 payment-channel-worldpay 实现层，隔离 XML 与 JSON 两种注册实现。
 * @status : create
 */
public final class WorldPayChannelCode {

    /** Worldpay Gateway XML 实现在 Registry 和渠道配置中的稳定编码。 */
    public static final String WPGXML = "WPGXML";

    /** Worldpay Gateway JSON 实现在 Registry 和渠道配置中的稳定编码。 */
    public static final String WPGJSON = "WPGJSON";

    private WorldPayChannelCode() {
    }
}
