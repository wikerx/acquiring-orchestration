package com.scott.payment.settlement.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementManagementDTOs
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 结算内部命令接口契约；操作人身份只能由 service-admin 的可信登录上下文注入。
 * @status : create
 */
public final class SettlementManagementDTOs {

    private SettlementManagementDTOs() {
    }

    /** Admin 受控命令；操作人由 Admin 登录上下文生成后再进入内部接口。 */
    @Data
    public static class BatchCommandRequest {
        /** 命令请求幂等键，不允许为空。 */
        private String requestKey;
        /** service-admin 查询到的批次 version，用于状态 CAS。 */
        private Long expectedVersion;
        /** 人工操作原因，不允许为空且不得包含敏感凭据。 */
        private String reason;
        /** 可信登录管理账户 ID，不接受浏览器自报身份。 */
        private Long operatorId;
        /** 可信登录管理账户展示名。 */
        private String operatorName;
        /** service-admin 授权后的角色权限快照。 */
        private String roleSnapshot;
        /** 操作客户端 IP 审计值。 */
        private String clientIp;
        /** 操作客户端 User-Agent 审计值。 */
        private String userAgent;
        /** service-admin 记录的操作时间，精度为毫秒。 */
        private LocalDateTime operationTime;
    }

    /** 入账前取消命令结果。 */
    @Data
    public static class BatchCommandResponse {
        /** 受控命令作用的正式结算批次号。 */
        private String settlementBatchNo;
        /** 命令产生的结果批次号；取消场景允许为空。 */
        private String resultBatchNo;
        /** 命令执行后的批次或操作状态。 */
        private String resultStatus;
        /** 入账前取消实际释放候选数；非取消场景允许为空。 */
        private Integer releasedCandidateCount;
        /** 人工恢复后重新进入异步处理的候选数；非恢复场景允许为空。 */
        private Integer restoredCandidateCount;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ReviewCandidateReference
     * @date : 2026-08-26 21:10
     * @email : scott_x@163.com
     * @description : 审核candidatereference嵌套数据模型，定义所属聚合内固定的字段集合和传递边界。
     * @status : create
     */
    @Data
    public static class ReviewCandidateReference {
        /** 待预审结算候选数据库主键。 */
        private Long candidateId;
        /** 页面读取的候选 version，用于防止过期选择。 */
        private Long expectedVersion;
    }

    /** service-admin 注入可信操作人后的预审提交命令。 */
    @Data
    public static class ReviewSubmitRequest {
        /** 预审创建请求幂等键，不允许为空。 */
        private String requestKey;
        /** REGULAR、RESERVE_RELEASE 或 ADJUSTMENT。 */
        private String reviewType;
        /** 结算日历业务日期。 */
        private LocalDate businessDate;
        /** 候选窗口闭区间起点。 */
        private LocalDateTime cutoffBeginTime;
        /** 候选窗口开区间终点。 */
        private LocalDateTime cutoffEndTime;
        /** 待锁定候选及其期望版本，不能为空且最多一千条。 */
        private List<ReviewCandidateReference> candidates = Collections.emptyList();
        /** Maker 提交原因，不允许为空。 */
        private String reason;
        /** service-admin 可信注入的 Maker 账户 ID。 */
        private Long operatorId;
        /** Maker 账户展示名。 */
        private String operatorName;
        /** Maker 提交时角色权限快照。 */
        private String roleSnapshot;
        /** Maker 客户端 IP 审计值。 */
        private String clientIp;
        /** Maker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Maker 实际提交时间，精度为毫秒。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信 Checker 后的预审终态决策命令。 */
    @Data
    public static class ReviewDecisionRequest {
        /** 预审决策请求幂等键，不允许为空。 */
        private String requestKey;
        /** 页面读取的预审单 version，用于终态 CAS。 */
        private Long expectedVersion;
        /** APPROVE、REJECT 或 CANCEL。 */
        private String decision;
        /** Checker 决策意见，不允许为空。 */
        private String comment;
        /** service-admin 可信注入的 Checker 账户 ID。 */
        private Long operatorId;
        /** Checker 账户展示名。 */
        private String operatorName;
        /** Checker 决策时角色权限快照。 */
        private String roleSnapshot;
        /** Checker 客户端 IP 审计值。 */
        private String clientIp;
        /** Checker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Checker 实际决策时间，精度为毫秒。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信操作人后的失败复核任务恢复命令。 */
    @Data
    public static class ReviewDecisionTaskResumeRequest {
        private String requestKey;
        private Long expectedVersion;
        private String reason;
        private Long operatorId;
        private String operatorName;
        private String roleSnapshot;
        private String clientIp;
        private String userAgent;
        private LocalDateTime operationTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ReviewCommandResponse
     * @date : 2026-08-26 21:10
     * @email : scott_x@163.com
     * @description : 审核command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class ReviewCommandResponse {
        /** 预审单业务号。 */
        private String reviewOrderNo;
        /** 预审当前或终态状态。 */
        private String reviewStatus;
        /** 批准后创建的正式结算批次号；其他状态为空。 */
        private String settlementBatchNo;
        /** 预审冻结候选总数。 */
        private Integer candidateCount;
        /** 统一目标 ISO 结算币种。 */
        private String targetCurrency;
        /** 目标币种 ISO 小数位。 */
        private Integer targetCurrencyExponent;
        /** 冻结净结果 CREDIT 或 DEBIT 方向。 */
        private String netDirection;
        /** 冻结非负净额，单位由 targetCurrencyExponent 决定。 */
        private BigDecimal netAmount;
        /** 决策时使用的预审单乐观锁版本。 */
        private Long version;
    }

    /** service-admin 注入可信 Maker 后创建服务端冻结的交易结算预览。 */
    @Data
    public static class ManualReviewPreviewRequest {
        private String requestKey;
        private String merchantId;
        private Long settlementProfileId;
        private String paymentType;
        private String paymentMethod;
        private String reason;
        private Long operatorId;
        private String operatorName;
        private String roleSnapshot;
        private String clientIp;
        private String userAgent;
        private LocalDateTime operationTime;
    }

    /** 将已冻结预览提交到后台异步生成队列。 */
    @Data
    public static class ManualReviewStartRequest {
        private String requestKey;
        private Long expectedVersion;
    }

    /** 按来源币种展示的候选统计，金额不得跨币种直接合计。 */
    @Data
    public static class ManualReviewPreviewLineResponse {
        private String sourceCurrency;
        private Integer sourceCurrencyExponent;
        private Long transactionCount;
        private BigDecimal grossAmount;
        private BigDecimal platformFeeAmount;
        private BigDecimal reserveAmount;
        private BigDecimal releasedReserveAmount;
        private BigDecimal netSettlementAmount;
        private Long pendingFeeCount;
        private String reserveDelayUnit;
        private Integer minimumReserveDelayDays;
        private Integer maximumReserveDelayDays;
        private LocalDate earliestExpectedReleaseDate;
        private LocalDate latestExpectedReleaseDate;
    }

    /** 手动交易结算预览及后台生成进度。 */
    @Data
    public static class ManualReviewTaskResponse {
        private String taskNo;
        private String reviewOrderNo;
        private String taskStatus;
        private String reviewType;
        private String merchantId;
        private Long settlementProfileId;
        private Long settlementAccountId;
        private String targetCurrency;
        private Integer targetCurrencyExponent;
        private String paymentType;
        private String paymentMethod;
        private String submitReason;
        private LocalDate businessDate;
        private LocalDateTime cutoffEndTime;
        private Long snapshotMaxCandidateId;
        private Integer expectedCandidateCount;
        private Integer processedCandidateCount;
        private Integer lockedCandidateCount;
        private Integer progressPercent;
        private String initialDelayUnit;
        private Integer initialDelayDays;
        private Integer regularDelayDays;
        private String settlementFrequency;
        private Integer frequencyDay;
        private List<ManualReviewPreviewLineResponse> preview = Collections.emptyList();
        private Integer retryCount;
        private String failureCode;
        private String failureMessage;
        private LocalDateTime startedTime;
        private LocalDateTime completedTime;
        private Long version;
    }

    /** 大批量预审单的异步审批、驳回或取消进度。 */
    @Data
    public static class ReviewDecisionTaskResponse {
        private String taskNo;
        private String reviewOrderNo;
        private String decisionAction;
        private String taskStatus;
        private Integer totalSegmentCount;
        private Integer processedSegmentCount;
        private Integer resultBatchCount;
        private Integer progressPercent;
        private String firstSettlementBatchNo;
        private Integer retryCount;
        private String failureCode;
        private String failureMessage;
        private LocalDateTime startedTime;
        private LocalDateTime completedTime;
        private Long version;
        private Boolean recoverable;
    }

    /** service-admin 注入可信 Maker 后的冲正申请。 */
    @Data
    public static class ReversalSubmitRequest {
        /** 冲正创建请求幂等键，不允许为空。 */
        private String requestKey;
        /** 待冲正的已入账正式结算批次号。 */
        private String originalBatchNo;
        /** 页面读取的原批次 version，用于冻结前校验。 */
        private Long expectedBatchVersion;
        /** Maker 冲正申请原因，不允许为空。 */
        private String reason;
        /** service-admin 可信注入的 Maker 账户 ID。 */
        private Long operatorId;
        /** Maker 账户展示名。 */
        private String operatorName;
        /** Maker 提交时角色权限快照。 */
        private String roleSnapshot;
        /** Maker 客户端 IP 审计值。 */
        private String clientIp;
        /** Maker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Maker 实际提交时间，精度为毫秒。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信 Checker 后的冲正决策。 */
    @Data
    public static class ReversalDecisionRequest {
        /** 冲正决策请求幂等键，不允许为空。 */
        private String requestKey;
        /** 页面读取的冲正单 version，用于终态 CAS。 */
        private Long expectedVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** Checker 决策意见，不允许为空。 */
        private String comment;
        /** service-admin 可信注入的 Checker 账户 ID。 */
        private Long operatorId;
        /** Checker 账户展示名。 */
        private String operatorName;
        /** Checker 决策时角色权限快照。 */
        private String roleSnapshot;
        /** Checker 客户端 IP 审计值。 */
        private String clientIp;
        /** Checker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Checker 实际决策时间，精度为毫秒。 */
        private LocalDateTime operationTime;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ReversalCommandResponse
     * @date : 2026-08-26 21:10
     * @email : scott_x@163.com
     * @description : 冲正command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class ReversalCommandResponse {
        /** 冲正申请单号。 */
        private String reversalOrderNo;
        /** PENDING_APPROVAL、APPROVED 或 REJECTED。 */
        private String reversalStatus;
        /** 被冲正的原正式批次号。 */
        private String originalBatchNo;
        /** 批准后创建的独立反向批次号；其他状态为空。 */
        private String reversalBatchNo;
        /** 原批次所属平台商户号。 */
        private String merchantId;
        /** 原批次目标 ISO 结算币种。 */
        private String currency;
        /** 原净结果 CREDIT 或 DEBIT 方向。 */
        private String netDirection;
        /** 原净结果非负金额，单位由 currency 的 ISO exponent 决定。 */
        private BigDecimal netAmount;
        /** 冲正单当前乐观锁版本。 */
        private Long version;
    }
}
