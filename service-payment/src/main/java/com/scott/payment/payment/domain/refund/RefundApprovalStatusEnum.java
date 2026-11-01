package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * 退款审批状态枚举，限定普通审批工作队列的单向状态流转。
 */
@Getter
public enum RefundApprovalStatusEnum {

    /**
     * 审批单等待有权限的操作人作出决定，允许迁移为通过、拒绝或过期。
     */
    PENDING("PENDING", false),

    /**
     * 审批已通过且退款执行事件已写入 Outbox，属于不可逆终态。
     */
    APPROVED("APPROVED", true),

    /**
     * 审批被人工拒绝，退款动作不得继续执行，属于不可逆终态。
     */
    REJECTED("REJECTED", true),

    /**
     * 审批在有效期内未完成而关闭，退款动作不得继续执行，属于不可逆终态。
     */
    EXPIRED("EXPIRED", true);

    private final String code;
    private final boolean terminal;

    /**
     * 创建审批状态并标记其是否为终态。
     *
     * @param code 持久化和接口使用的稳定状态编码
     * @param terminal 是否禁止继续发生状态迁移
     */
    RefundApprovalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
