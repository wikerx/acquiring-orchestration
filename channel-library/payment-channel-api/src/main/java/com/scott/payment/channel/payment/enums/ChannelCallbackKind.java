package com.scott.payment.channel.payment.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackKind
 * @date : 2026-08-13 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调业务类型，位于 payment-channel-api 枚举层，用于区分 3DS 认证事件与资金交易事件；不包含任何 Provider 协议字段。
 * @status : create
 */
public enum ChannelCallbackKind {

    /** 3DS Method、Authenticate Payer 或认证交易通知，不得直接推进资金终态。 */
    THREE_DS_AUTHENTICATION,

    /** 支付、授权、请款、退款等资金交易通知，可按平台状态机处理。 */
    FINANCIAL_TRANSACTION
}
