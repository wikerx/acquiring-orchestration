package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 支付尝试状态枚举。
 */
@Getter
public enum PaymentCheckoutAttemptStatusEnum {

    /** 已创建支付尝试，尚未提交卡数据。 */
    INIT("INIT", false),
    /** 卡数据已完成校验并进入支付处理链路。 */
    CARD_SUBMITTED("CARD_SUBMITTED", false),
    /** 已向渠道发起 3DS 认证。 */
    THREE_DS_INITIATED("THREE_DS_INITIATED", false),
    /** 需要付款人在浏览器完成 3DS 挑战。 */
    THREE_DS_REQUIRED("THREE_DS_REQUIRED", false),
    /** 已收到 3DS 浏览器回跳，等待服务端核验。 */
    THREE_DS_RETURNED("THREE_DS_RETURNED", false),
    /** 3DS 服务端认证已通过，可继续提交渠道支付。 */
    THREE_DS_PASSED("THREE_DS_PASSED", false),
    /** 3DS 认证失败，本次尝试终止。 */
    THREE_DS_FAILED("THREE_DS_FAILED", true),
    /** 支付请求已提交渠道，等待渠道结果。 */
    CHANNEL_SUBMITTED("CHANNEL_SUBMITTED", false),
    /** 本次支付尝试成功终态。 */
    SUCCEEDED("SUCCEEDED", true),
    /** 本次支付尝试失败终态。 */
    FAILED("FAILED", true),
    /** 渠道结果尚未确定，需要查询或回调推进。 */
    PROCESSING("PROCESSING", false),
    /** 本次尝试被新尝试替代或主动放弃的终态。 */
    ABANDONED("ABANDONED", true);

    /** 持久化和内部协议使用的稳定状态编码。 */
    private final String code;

    /** 是否禁止再推进本次支付尝试状态。 */
    private final boolean terminal;

    PaymentCheckoutAttemptStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
