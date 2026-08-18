package com.scott.payment.admin.support.approval;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessApprovalStatus
 * @date : 2026-08-06 00:00
 * @description : 商户来源网址和 IP 白名单共用的审批状态，约束待审、通过、拒绝三种状态及其交易状态组合。
 * @status : create
 */
public enum MerchantAccessApprovalStatus {

    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final int code;

    MerchantAccessApprovalStatus(int code) {
        this.code = code;
    }

    /**
     * 返回数据库状态码。
     *
     * @return 0 待审核、1 审核通过、2 审核拒绝
     */
    public int code() {
        return code;
    }

    /**
     * 按审批结果解析交易状态。
     *
     * @param requestedStatus 审核通过时人工选择的交易状态；为空默认允许交易
     * @return 1 允许交易或 0 禁止交易；待审核和审核拒绝固定返回 0
     * @throws IllegalArgumentException 审核通过但交易状态不是 0 或 1 时抛出
     */
    public int transactionStatus(Integer requestedStatus) {
        if (this != APPROVED) {
            return 0;
        }
        if (requestedStatus == null) {
            return 1;
        }
        if (requestedStatus == 0 || requestedStatus == 1) {
            return requestedStatus;
        }
        throw new IllegalArgumentException("status must be 0 or 1");
    }

    /**
     * 校验并解析审批状态码。
     *
     * @param code 审批状态码
     * @return 对应枚举
     * @throws IllegalArgumentException 状态码不受支持时抛出
     */
    public static MerchantAccessApprovalStatus fromCode(Integer code) {
        for (MerchantAccessApprovalStatus value : values()) {
            if (code != null && value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("approvalStatus must be 0, 1 or 2");
    }
}
