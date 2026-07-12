package com.scott.payment.payout.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 代付交易状态枚举，位于 service-payout 领域状态层，用于收敛平台代付状态取值，避免核心链路散落状态字符串。
 * @status : create
 */
@Getter
public enum PayoutTransactionStatusEnum {

    /**
     * 代付服务已接收交易，后续结果以查询、回调或通知为准。
     */
    RECEIVED("RECEIVED");

    private final String code;

    /**
     * 创建代付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PayoutTransactionStatusEnum(String code) {
        this.code = code;
    }
}
