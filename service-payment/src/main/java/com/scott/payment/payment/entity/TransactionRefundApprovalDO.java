package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionRefundApprovalDO
 * @date : 2026-08-06 00:00
 * @description : 退款审批工作队列实体，位于支付持久化层；该普通表只保存审批事实和三个真实分片时间，不作为退款金额事实源。
 * @status : create
 */
@Data
@TableName("transaction_refund_approval")
public class TransactionRefundApprovalDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalId;
    private String refundTransactionId;
    private LocalDateTime refundTransactionDateTime;
    private String sourceTransactionId;
    private LocalDateTime sourceTransactionDateTime;
    private LocalDateTime rootTransactionDateTime;
    private String merchantId;
    private String approvalStatus;
    private String approvalPolicyCode;
    private String approvalPolicySnapshot;
    private Integer currentApprovalLevel;
    private Integer totalApprovalLevels;
    private String applicantType;
    private String applicantId;
    private String applicantName;
    private String approvalOperatorId;
    private String approvalOperatorName;
    private LocalDateTime approvalTime;
    private String approvalReason;
    private LocalDateTime expireTime;
    private String decisionRequestId;
    private String executionEventId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
