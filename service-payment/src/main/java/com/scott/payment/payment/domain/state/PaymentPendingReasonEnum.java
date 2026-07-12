package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentPendingReasonEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易挂起原因枚举，位于 service-payment 领域状态层，用于在 transaction_status=PENDING 时区分等待 3DS、渠道异步和争议处理等场景。
 * @status : create
 */
@Getter
public enum PaymentPendingReasonEnum {

    /**
     * 等待付款人完成 3DS 认证或渠道跳转。
     */
    NEED_REDIRECT("NEED_REDIRECT"),

    /**
     * 等待风控人工复核结果。
     */
    RISK_REVIEW("RISK_REVIEW"),

    /**
     * 等待渠道异步回调。
     */
    WAITING_CHANNEL_CALLBACK("WAITING_CHANNEL_CALLBACK"),

    /**
     * 拒付或调单争议处理中。
     */
    DISPUTE_IN_PROGRESS("DISPUTE_IN_PROGRESS");

    private final String code;

    /**
     * 创建挂起原因。
     *
     * @param code 挂起原因编码
     */
    PaymentPendingReasonEnum(String code) {
        this.code = code;
    }
}
