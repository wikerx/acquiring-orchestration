package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalStatusEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款审批状态枚举，限定普通审批工作队列的单向状态流转。
 * @status : create
 */
@Getter
public enum RefundApprovalStatusEnum {

    /**
     * PENDING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PENDING("PENDING", false),
    /**
     * APPROVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    APPROVED("APPROVED", true),
    /**
     * REJECTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REJECTED("REJECTED", true),
    /**
     * EXPIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    EXPIRED("EXPIRED", true);

    private final String code;
    private final boolean terminal;

    RefundApprovalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
