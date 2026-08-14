package com.scott.payment.channel.payment.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelThreeDsPhase
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一 3DS 阶段，位于 payment-channel-api 枚举层，用于分隔初始化与付款人认证，禁止跨浏览器交互连续调用渠道 API。
 * @status : create
 */
public enum ChannelThreeDsPhase {

    /** 初始化 3DS，并判断是否需要在浏览器执行 3DS Method。 */
    INITIALIZE,

    /** 3DS Method 完成或无需执行后，认证付款人并判断是否需要 ACS Challenge。 */
    AUTHENTICATE,

    /** 浏览器完成 ACS Challenge 后查询渠道认证交易，确认最终认证状态。 */
    VERIFY
}
