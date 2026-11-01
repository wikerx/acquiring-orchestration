package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个大批量预审单的可恢复 Maker-Checker 决策任务。
 */
@Data
@TableName("settlement_review_decision_task")
public class SettlementReviewDecisionTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 持久化的{@code taskNo}，用于还原当前记录的业务事实。
     */
    private String taskNo;
    /**
     * 持久化的请求密钥，用于还原当前记录的业务事实。
     */
    private String requestKey;
    /**
     * 持久化的审核订单编号，用于还原当前记录的业务事实。
     */
    private String reviewOrderNo;

    private Long expectedReviewVersion;
    /**
     * 持久化的结论动作，用于还原当前记录的业务事实。
     */
    private String decisionAction;
    /**
     * 持久化的{@code decisionComment}，用于还原当前记录的业务事实。
     */
    private String decisionComment;

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
     * 持久化的动作时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime operationTime;

    private Integer totalSegmentCount;

    private Integer processedSegmentCount;

    private Integer resultBatchCount;
    /**
     * 持久化的首个结算批次编号，用于还原当前记录的业务事实。
     */
    private String firstSettlementBatchNo;

    private String taskStatus;
    /**
     * 持久化的{@code processingOwner}，用于还原当前记录的业务事实。
     */
    private String processingOwner;
    /**
     * 持久化的{@code processingDeadline}，用于还原当前记录的业务事实。
     */
    private LocalDateTime processingDeadline;
    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer retryCount;
    /**
     * 持久化的{@code nextRetryTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime nextRetryTime;

    private String lastFailureCode;
    /**
     * 持久化的{@code lastFailureMessage}，用于还原当前记录的业务事实。
     */
    private String lastFailureMessage;
    /**
     * 持久化的{@code startedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime startedTime;
    /**
     * 持久化的{@code completedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime completedTime;

    private Long version;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
