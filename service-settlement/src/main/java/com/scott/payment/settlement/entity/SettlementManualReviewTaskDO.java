package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 服务端冻结筛选条件并分段生成交易或保证金结算预审单的持久化任务。 */
@Data
@TableName("settlement_manual_review_task")
public class SettlementManualReviewTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String requestKey;
    private String submitRequestKey;
    private String reviewOrderNo;
    private String reviewType;
    private String merchantId;
    private Long settlementProfileId;
    private Long settlementAccountId;
    private String targetCurrency;
    private Integer targetCurrencyExponent;
    private String paymentType;
    private String paymentMethod;
    private LocalDate businessDate;
    private String businessTimeZone;
    private LocalDateTime cutoffBeginTime;
    private LocalDateTime cutoffEndTime;
    private Long snapshotMaxCandidateId;
    private Integer expectedCandidateCount;
    private Integer processedCandidateCount;
    private Integer lockedCandidateCount;
    private Long lastCandidateId;
    private String initialDelayUnit;
    private Integer initialDelayDays;
    private Integer regularDelayDays;
    private String settlementFrequency;
    private Integer frequencyDay;
    private String previewJson;
    private String taskStatus;
    private Long submittedByAccountId;
    private String submittedByAccountName;
    private String submittedRoleSnapshot;
    private String submitClientIp;
    private String submitUserAgent;
    private String submitReason;
    private LocalDateTime operationTime;
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
