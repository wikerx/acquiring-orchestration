package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalResultDTO
 * @date : 2026-08-06 15:30
 * @email : scott_x@163.com
 * @description : Payment 内部退款审批结果，仅返回 Admin 刷新当前行所需的审批事实。
 * @status : create
 */
@Data
public class RefundApprovalResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String approvalId;
    private String refundTransactionId;
    private String approvalStatus;
    private String approvalOperatorId;
    private String approvalOperatorName;
    private LocalDateTime approvalTime;
    private String approvalReason;
    private String executionEventId;
    private Integer version;
}
