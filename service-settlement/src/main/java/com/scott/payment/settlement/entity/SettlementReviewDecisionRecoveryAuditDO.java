package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 失败的分段复核任务由人工恢复时保存的不可变审计快照。 */
@Data
@TableName("settlement_review_decision_recovery_audit")
public class SettlementReviewDecisionRecoveryAuditDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String reviewOrderNo;
    private String requestKey;
    private Long expectedVersion;
    private String taskStatusBefore;
    private Integer processedSegmentCountBefore;
    private Integer resultBatchCountBefore;
    private Integer retryCountBefore;
    private String failureCodeBefore;
    private String failureMessageBefore;
    private LocalDateTime startedTimeBefore;
    private LocalDateTime completedTimeBefore;
    private Long operatorAccountId;
    private String operatorAccountName;
    private String operatorRoleSnapshot;
    private String clientIp;
    private String userAgent;
    private String reason;
    private LocalDateTime operationTime;
    private LocalDateTime recoveredTime;
    private LocalDateTime createTime;
}
