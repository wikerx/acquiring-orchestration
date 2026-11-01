package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * 支付处理阶段枚举，区分风控、路由、渠道调用、等待交互和完成等受控阶段。
 */
@Getter
public enum PaymentProcessStageEnum {

    /**
     * 交易已被 payment 服务受理。
     */
    ACCEPTED("ACCEPTED"),

    /**
     * 正在执行路由前风控。
     */
    RISK_CHECKING("RISK_CHECKING"),

    /**
     * 正在选择渠道和 MID。
     */
    ROUTING("ROUTING"),

    /**
     * 正在调用渠道。
     */
    CHANNEL_REQUESTING("CHANNEL_REQUESTING"),

    /**
     * 渠道已受理，等待同步或异步结果。
     */
    CHANNEL_PROCESSING("CHANNEL_PROCESSING"),

    /**
     * 等待付款人完成 3DS 或渠道跳转动作。
     */
    WAITING_3DS("WAITING_3DS"),

    /**
     * 等待风控人工复核。
     */
    WAITING_RISK_REVIEW("WAITING_RISK_REVIEW"),

    /**
     * 退款申请等待运营审批，尚未向渠道发起请求。
     */
    WAITING_APPROVAL("WAITING_APPROVAL"),

    /**
     * 退款审批已通过，等待 MQ 消费者抢占并执行固定渠道请求。
     */
    WAITING_EXECUTION("WAITING_EXECUTION"),

    /**
     * 等待渠道异步回调。
     */
    WAITING_CALLBACK("WAITING_CALLBACK"),

    /**
     * 当前交易动作处理完成。
     */
    FINISHED("FINISHED");

    private final String code;

    /**
     * 创建内部处理阶段。
     *
     * @param code 内部处理阶段编码
     */
    PaymentProcessStageEnum(String code) {
        this.code = code;
    }
}
