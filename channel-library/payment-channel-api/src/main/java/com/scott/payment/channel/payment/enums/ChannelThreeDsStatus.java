package com.scott.payment.channel.payment.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelThreeDsStatus
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一 3DS 认证状态，位于 payment-channel-api 枚举层，只描述认证结果，不代表支付交易终态。
 * @status : create
 */
public enum ChannelThreeDsStatus {

    /** 初始化完成且无需执行 3DS Method，可以继续调用付款人认证。 */
    READY_TO_AUTHENTICATE,

    /** 初始化返回 3DS Method HTML，需要先在隐藏 iframe 中执行，不能立即认证付款人。 */
    METHOD_REQUIRED,

    /** 认证已通过，可以继续提交支付或授权。 */
    PASSED,

    /** 需要付款人在受控收银台完成 ACS 质询。 */
    CHALLENGE_REQUIRED,

    /** 渠道已明确返回认证失败。 */
    FAILED,

    /** 认证结果尚未确定，需要通过查询或回调继续确认。 */
    PROCESSING
}
