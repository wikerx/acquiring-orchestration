package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一个大批量预审单的可恢复 Maker-Checker 决策任务。 */
@Data
@TableName("settlement_review_decision_task")
public class SettlementReviewDecisionTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String requestKey;
    private String reviewOrderNo;
    private Long expectedReviewVersion;
    private String decisionAction;
    private String decisionComment;
    private Long operatorAccountId;
    private String operatorAccountName;
    private String operatorRoleSnapshot;
    private String clientIp;
    private String userAgent;
    private LocalDateTime operationTime;
    private Integer totalSegmentCount;
    private Integer processedSegmentCount;
    private Integer resultBatchCount;
    private String firstSettlementBatchNo;
    private String taskStatus;
    private String processingOwner;
    private LocalDateTime processingDeadline;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastFailureCode;
    private String lastFailureMessage;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
