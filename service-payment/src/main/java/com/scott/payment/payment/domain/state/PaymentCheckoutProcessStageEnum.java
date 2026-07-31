package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 内部处理阶段枚举。
 */
@Getter
public enum PaymentCheckoutProcessStageEnum {

    /** 会话及访问令牌已创建。 */
    SESSION_CREATED("SESSION_CREATED"),
    /** 等待付款人打开收银台或提交支付。 */
    WAITING_PAYER("WAITING_PAYER"),
    /** 正在校验支付方式和卡数据。 */
    CARD_VALIDATE("CARD_VALIDATE"),
    /** 卡数据已提交到受控支付链路。 */
    CARD_SUBMITTED("CARD_SUBMITTED"),
    /** 正在准备并发起 3DS。 */
    INITIATE_3DS("INITIATE_3DS"),
    /** 正在请求渠道认证付款人。 */
    AUTHENTICATE_PAYER("AUTHENTICATE_PAYER"),
    /** 等待付款人完成 3DS 挑战。 */
    WAITING_3DS("WAITING_3DS"),
    /** 正在向支付渠道提交交易。 */
    SUBMIT_CHANNEL("SUBMIT_CHANNEL"),
    /** 等待渠道同步、回调或查询结果。 */
    WAITING_CHANNEL("WAITING_CHANNEL"),
    /** 已生成供付款人展示的结果页面。 */
    RESULT_RENDERED("RESULT_RENDERED");

    /** 持久化审计使用的稳定处理阶段编码。 */
    private final String code;

    PaymentCheckoutProcessStageEnum(String code) {
        this.code = code;
    }
}
