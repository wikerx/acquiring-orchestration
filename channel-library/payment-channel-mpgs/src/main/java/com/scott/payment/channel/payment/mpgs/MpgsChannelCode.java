package com.scott.payment.channel.payment.mpgs;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsChannelCode
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : MPGS provider 渠道编码常量，位于 payment-channel-mpgs 实现层，避免公共渠道 API 枚举具体 PSP。
 * @status : create
 */
public final class MpgsChannelCode {

    /** MPGS provider 在 Registry 和渠道配置中的稳定编码。 */
    public static final String MPGS = "MPGS";

    private MpgsChannelCode() {
    }
}
