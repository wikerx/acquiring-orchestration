package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务端冻结筛选条件并分段生成交易或保证金结算预审单的持久化任务。
 */
@Data
@TableName("settlement_manual_review_task")
public class SettlementManualReviewTaskDO {

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
     * 持久化的提交请求密钥，用于还原当前记录的业务事实。
     */
    private String submitRequestKey;
    /**
     * 持久化的审核订单编号，用于还原当前记录的业务事实。
     */
    private String reviewOrderNo;
    /**
     * 审核类型，用于区分 {@code SettlementManualReviewTaskDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String reviewType;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private Long settlementProfileId;

    private Long settlementAccountId;

    private String targetCurrency;

    private Integer targetCurrencyExponent;
    /**
     * 支付类型，用于区分 {@code SettlementManualReviewTaskDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String paymentType;

    private String paymentMethod;
    /**
     * 持久化的{@code businessDate}，用于还原当前记录的业务事实。
     */
    private LocalDate businessDate;
    /**
     * {@code businessTimeZone}，使用 IANA 时区标识解释关联的本地日期时间。
     */
    private String businessTimeZone;
    /**
     * 持久化的{@code cutoffBeginTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime cutoffBeginTime;
    /**
     * 持久化的{@code cutoffEndTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime cutoffEndTime;

    private Long snapshotMaxCandidateId;

    private Integer expectedCandidateCount;

    private Integer processedCandidateCount;

    private Integer lockedCandidateCount;

    private Long lastCandidateId;
    /**
     * 持久化的{@code initialDelayUnit}，用于还原当前记录的业务事实。
     */
    private String initialDelayUnit;
    /**
     * 持久化的初始延迟天数，用于还原当前记录的业务事实。
     */
    private Integer initialDelayDays;
    /**
     * 持久化的{@code regularDelayDays}，用于还原当前记录的业务事实。
     */
    private Integer regularDelayDays;
    /**
     * 持久化的结算频率，用于还原当前记录的业务事实。
     */
    private String settlementFrequency;
    /**
     * 持久化的频率日，用于还原当前记录的业务事实。
     */
    private Integer frequencyDay;
    /**
     * 持久化的{@code previewJson}，用于还原当前记录的业务事实。
     */
    private String previewJson;

    private String taskStatus;

    private Long submittedByAccountId;

    private String submittedByAccountName;
    /**
     * 冲正申请人提交时的角色快照，用于 Maker-Checker 审计。
     */
    private String submittedRoleSnapshot;
    /**
     * 冲正申请提交端 IP 快照，仅用于安全审计，展示和日志必须脱敏。
     */
    private String submitClientIp;
    /**
     * 冲正申请提交端 User-Agent 快照，用于安全审计并限制长度。
     */
    private String submitUserAgent;
    /**
     * 持久化的提交原因，用于还原当前记录的业务事实。
     */
    private String submitReason;
    /**
     * 持久化的动作时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime operationTime;
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
