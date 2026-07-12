package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 收单支付交易状态枚举，位于 service-payment 领域状态层，用于收敛平台支付状态取值，避免核心链路散落状态字符串。
 * @status : create
 */
@Getter
public enum PaymentTransactionStatusEnum {

    /**
     * 支付服务已接收交易，后续结果以查询、回调或通知为准。
     */
    RECEIVED("RECEIVED");

    private final String code;

    /**
     * 创建收单支付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PaymentTransactionStatusEnum(String code) {
        this.code = code;
    }
}
