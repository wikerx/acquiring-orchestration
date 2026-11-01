package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * 支付等待原因枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
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
     * 渠道请求可能已经送达，但同步响应未提供可验证结果，必须等待查询或回调勾兑。
     */
    CHANNEL_RESULT_UNKNOWN("CHANNEL_RESULT_UNKNOWN"),

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
