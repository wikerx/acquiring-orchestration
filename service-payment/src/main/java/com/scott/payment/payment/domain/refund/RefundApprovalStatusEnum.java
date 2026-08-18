package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalStatusEnum
 * @date : 2026-08-06 00:00
 * @description : 退款审批状态枚举，限定普通审批工作队列的单向状态流转。
 * @status : create
 */
@Getter
public enum RefundApprovalStatusEnum {

    PENDING("PENDING", false),
    APPROVED("APPROVED", true),
    REJECTED("REJECTED", true),
    EXPIRED("EXPIRED", true);

    private final String code;
    private final boolean terminal;

    RefundApprovalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
