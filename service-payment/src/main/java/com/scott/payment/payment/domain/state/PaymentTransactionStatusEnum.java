package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * 支付交易状态枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 */
@Getter
public enum PaymentTransactionStatusEnum {

    /**
     * 交易已成功。
     */
    SUCCESS("SUCCESS", true),

    /**
     * 交易已失败，失败原因由失败原因码进一步区分。
     */
    FAILED("FAILED", true),

    /**
     * 交易等待外部动作或异步结果，例如 3DS 跳转、渠道异步回调、拒付处理中。
     */
    PENDING("PENDING", false),

    /**
     * 交易处理中，例如风控、路由、渠道请求、回调处理。
     */
    PROCESSING("PROCESSING", false);

    private final String code;

    private final boolean terminal;

    /**
     * 创建收单支付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PaymentTransactionStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
