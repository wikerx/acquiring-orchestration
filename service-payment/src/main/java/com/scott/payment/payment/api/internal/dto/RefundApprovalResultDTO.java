package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Payment 内部退款审批结果，仅返回 Admin 刷新当前行所需的审批事实。
 */
@Data
public class RefundApprovalResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private String approvalId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String refundTransactionId;

    private String approvalStatus;

    private String approvalOperatorId;

    private String approvalOperatorName;

    private LocalDateTime approvalTime;

    private String approvalReason;

    private String executionEventId;

    private Integer version;
}
