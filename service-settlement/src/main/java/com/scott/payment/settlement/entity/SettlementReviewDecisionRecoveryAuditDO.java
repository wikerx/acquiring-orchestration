package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败的分段复核任务由人工恢复时保存的不可变审计快照。
 */
@Data
@TableName("settlement_review_decision_recovery_audit")
public class SettlementReviewDecisionRecoveryAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 持久化的{@code taskNo}，用于还原当前记录的业务事实。
     */
    private String taskNo;
    /**
     * 持久化的审核订单编号，用于还原当前记录的业务事实。
     */
    private String reviewOrderNo;
    /**
     * 持久化的请求密钥，用于还原当前记录的业务事实。
     */
    private String requestKey;

    private Long expectedVersion;

    private String taskStatusBefore;

    private Integer processedSegmentCountBefore;

    private Integer resultBatchCountBefore;
    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer retryCountBefore;

    private String failureCodeBefore;
    /**
     * 持久化的{@code failureMessageBefore}，用于还原当前记录的业务事实。
     */
    private String failureMessageBefore;
    /**
     * 持久化的{@code startedTimeBefore}，用于还原当前记录的业务事实。
     */
    private LocalDateTime startedTimeBefore;
    /**
     * 持久化的{@code completedTimeBefore}，用于还原当前记录的业务事实。
     */
    private LocalDateTime completedTimeBefore;

    private Long operatorAccountId;

    private String operatorAccountName;
    /**
     * 持久化的{@code operatorRoleSnapshot}，用于还原当前记录的业务事实。
     */
    private String operatorRoleSnapshot;
    /**
     * 持久化的{@code clientIp}，用于还原当前记录的业务事实。
     */
    private String clientIp;
    /**
     * 持久化的{@code userAgent}，用于还原当前记录的业务事实。
     */
    private String userAgent;
    /**
     * 持久化的原因，用于还原当前记录的业务事实。
     */
    private String reason;
    /**
     * 持久化的动作时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime operationTime;
    /**
     * 持久化的{@code recoveredTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime recoveredTime;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
