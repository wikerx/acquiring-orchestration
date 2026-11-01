package com.scott.payment.settlement.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 结算内部命令接口契约；操作人身份只能由 service-admin 的可信登录上下文注入。
 */
public final class SettlementManagementDTOs {

    private SettlementManagementDTOs() {
    }

    /**
     * Admin 受控命令；操作人由 Admin 登录上下文生成后再进入内部接口。
     */
    @Data
    public static class BatchCommandRequest {
        /**
         * 命令请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * service-admin 查询到的批次 version，用于状态 CAS。
         */
        private Long expectedVersion;
        /**
         * 人工操作原因，不允许为空且不得包含敏感凭据。
         */
        private String reason;
        /**
         * 可信登录管理账户 ID，不接受浏览器自报身份。
         */
        private Long operatorId;
        /**
         * 可信登录管理账户展示名。
         */
        private String operatorName;
        /**
         * service-admin 授权后的角色权限快照。
         */
        private String roleSnapshot;
        /**
         * 操作客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * 操作客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * service-admin 记录的操作时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 入账前取消命令结果。
     */
    @Data
    public static class BatchCommandResponse {
        /**
         * 受控命令作用的正式结算批次号。
         */
        private String settlementBatchNo;
        /**
         * 命令产生的结果批次号；取消场景允许为空。
         */
        private String resultBatchNo;
        /**
         * 命令执行后的批次或操作状态。
         */
        private String resultStatus;
        /**
         * 入账前取消实际释放候选数；非取消场景允许为空。
         */
        private Integer releasedCandidateCount;
        /**
         * 人工恢复后重新进入异步处理的候选数；非恢复场景允许为空。
         */
        private Integer restoredCandidateCount;
    }

    /**
     * 审核candidatereference嵌套数据模型，定义所属聚合内固定的字段集合和传递边界。
     */
    @Data
    public static class ReviewCandidateReference {
        /**
         * 待预审结算候选数据库主键。
         */
        private Long candidateId;
        /**
         * 页面读取的候选 version，用于防止过期选择。
         */
        private Long expectedVersion;
    }

    /**
     * service-admin 注入可信操作人后的预审提交命令。
     */
    @Data
    public static class ReviewSubmitRequest {
        /**
         * 预审创建请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * REGULAR、RESERVE_RELEASE 或 ADJUSTMENT。
         */
        private String reviewType;
        /**
         * 结算日历业务日期。
         */
        private LocalDate businessDate;
        /**
         * 候选窗口闭区间起点。
         */
        private LocalDateTime cutoffBeginTime;
        /**
         * 候选窗口开区间终点。
         */
        private LocalDateTime cutoffEndTime;
        /**
         * 待锁定候选及其期望版本，不能为空且最多一千条。
         */
        private List<ReviewCandidateReference> candidates = Collections.emptyList();
        /**
         * Maker 提交原因，不允许为空。
         */
        private String reason;
        /**
         * service-admin 可信注入的 Maker 账户 ID。
         */
        private Long operatorId;
        /**
         * Maker 账户展示名。
         */
        private String operatorName;
        /**
         * Maker 提交时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Maker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Maker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Maker 实际提交时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信 Checker 后的预审终态决策命令。
     */
    @Data
    public static class ReviewDecisionRequest {
        /**
         * 预审决策请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 页面读取的预审单 version，用于终态 CAS。
         */
        private Long expectedVersion;
        /**
         * APPROVE、REJECT 或 CANCEL。
         */
        private String decision;
        /**
         * Checker 决策意见，不允许为空。
         */
        private String comment;
        /**
         * service-admin 可信注入的 Checker 账户 ID。
         */
        private Long operatorId;
        /**
         * Checker 账户展示名。
         */
        private String operatorName;
        /**
         * Checker 决策时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Checker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Checker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Checker 实际决策时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信操作人后的失败复核任务恢复命令。
     */
    @Data
    public static class ReviewDecisionTaskResumeRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         */
        private String requestKey;

        private Long expectedVersion;
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         */
        private String reason;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         */
        private String operatorName;
        /**
         * 请求中的角色快照，用于限定本次操作的输入和校验范围。
         */
        private String roleSnapshot;
        /**
         * 请求中的{@code clientIp}，用于限定本次操作的输入和校验范围。
         */
        private String clientIp;
        /**
         * 请求中的{@code userAgent}，用于限定本次操作的输入和校验范围。
         */
        private String userAgent;
        /**
         * 请求中的动作时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 审核command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class ReviewCommandResponse {
        /**
         * 预审单业务号。
         */
        private String reviewOrderNo;
        /**
         * 预审当前或终态状态。
         */
        private String reviewStatus;
        /**
         * 批准后创建的正式结算批次号；其他状态为空。
         */
        private String settlementBatchNo;
        /**
         * 预审冻结候选总数。
         */
        private Integer candidateCount;
        /**
         * 统一目标 ISO 结算币种。
         */
        private String targetCurrency;
        /**
         * 目标币种 ISO 小数位。
         */
        private Integer targetCurrencyExponent;
        /**
         * 冻结净结果 CREDIT 或 DEBIT 方向。
         */
        private String netDirection;
        /**
         * 冻结非负净额，单位由 targetCurrencyExponent 决定。
         */
        private BigDecimal netAmount;
        /**
         * 决策时使用的预审单乐观锁版本。
         */
        private Long version;
    }

    /**
     * service-admin 注入可信 Maker 后创建服务端冻结的交易结算预览。
     */
    @Data
    public static class ManualReviewPreviewRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         */
        private String requestKey;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;

        private Long settlementProfileId;
        /**
         * 支付类型，用于区分 {@code ManualReviewPreviewRequest} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String paymentType;

        private String paymentMethod;
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         */
        private String reason;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         */
        private String operatorName;
        /**
         * 请求中的角色快照，用于限定本次操作的输入和校验范围。
         */
        private String roleSnapshot;
        /**
         * 请求中的{@code clientIp}，用于限定本次操作的输入和校验范围。
         */
        private String clientIp;
        /**
         * 请求中的{@code userAgent}，用于限定本次操作的输入和校验范围。
         */
        private String userAgent;
        /**
         * 请求中的动作时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 将已冻结预览提交到后台异步生成队列。
     */
    @Data
    public static class ManualReviewStartRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         */
        private String requestKey;

        private Long expectedVersion;
    }

    /**
     * 按来源币种展示的候选统计，金额不得跨币种直接合计。
     */
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
        /**
         * 响应中的{@code reserveDelayUnit}，用于管理端或商户端展示当前处理结果。
         */
        private String reserveDelayUnit;
        /**
         * 响应中的{@code minimumReserveDelayDays}，用于管理端或商户端展示当前处理结果。
         */
        private Integer minimumReserveDelayDays;
        /**
         * 响应中的{@code maximumReserveDelayDays}，用于管理端或商户端展示当前处理结果。
         */
        private Integer maximumReserveDelayDays;
        /**
         * 响应中的{@code earliestExpectedReleaseDate}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDate earliestExpectedReleaseDate;
        /**
         * 响应中的{@code latestExpectedReleaseDate}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDate latestExpectedReleaseDate;
    }

    /**
     * 手动交易结算预览及后台生成进度。
     */
    @Data
    public static class ManualReviewTaskResponse {
        /**
         * 响应中的{@code taskNo}，用于管理端或商户端展示当前处理结果。
         */
        private String taskNo;
        /**
         * 响应中的审核订单编号，用于管理端或商户端展示当前处理结果。
         */
        private String reviewOrderNo;

        private String taskStatus;
        /**
         * 审核类型，用于区分 {@code ManualReviewTaskResponse} 记录的处理类别、配置维度或外部协议枚举。
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
         * 支付类型，用于区分 {@code ManualReviewTaskResponse} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String paymentType;

        private String paymentMethod;
        /**
         * 响应中的提交原因，用于管理端或商户端展示当前处理结果。
         */
        private String submitReason;
        /**
         * 响应中的{@code businessDate}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDate businessDate;
        /**
         * 响应中的{@code cutoffEndTime}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDateTime cutoffEndTime;

        private Long snapshotMaxCandidateId;

        private Integer expectedCandidateCount;

        private Integer processedCandidateCount;

        private Integer lockedCandidateCount;
        /**
         * 响应中的{@code progressPercent}，用于管理端或商户端展示当前处理结果。
         */
        private Integer progressPercent;
        /**
         * 响应中的{@code initialDelayUnit}，用于管理端或商户端展示当前处理结果。
         */
        private String initialDelayUnit;
        /**
         * 响应中的初始延迟天数，用于管理端或商户端展示当前处理结果。
         */
        private Integer initialDelayDays;
        /**
         * 响应中的{@code regularDelayDays}，用于管理端或商户端展示当前处理结果。
         */
        private Integer regularDelayDays;
        /**
         * 响应中的结算频率，用于管理端或商户端展示当前处理结果。
         */
        private String settlementFrequency;
        /**
         * 响应中的频率日，用于管理端或商户端展示当前处理结果。
         */
        private Integer frequencyDay;
        /**
         * 预览集合，承载 {@code ManualReviewTaskResponse} 当前请求或响应中的多值数据。
         */
        private List<ManualReviewPreviewLineResponse> preview = Collections.emptyList();
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         */
        private Integer retryCount;
        /**
         * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
         */
        private String failureCode;
        /**
         * 响应中的失败说明，用于管理端或商户端展示当前处理结果。
         */
        private String failureMessage;
        /**
         * 响应中的{@code startedTime}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDateTime startedTime;
        /**
         * 响应中的{@code completedTime}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDateTime completedTime;

        private Long version;
    }

    /**
     * 大批量预审单的异步审批、驳回或取消进度。
     */
    @Data
    public static class ReviewDecisionTaskResponse {
        /**
         * 响应中的{@code taskNo}，用于管理端或商户端展示当前处理结果。
         */
        private String taskNo;
        /**
         * 响应中的审核订单编号，用于管理端或商户端展示当前处理结果。
         */
        private String reviewOrderNo;
        /**
         * 响应中的结论动作，用于管理端或商户端展示当前处理结果。
         */
        private String decisionAction;

        private String taskStatus;

        private Integer totalSegmentCount;

        private Integer processedSegmentCount;

        private Integer resultBatchCount;
        /**
         * 响应中的{@code progressPercent}，用于管理端或商户端展示当前处理结果。
         */
        private Integer progressPercent;
        /**
         * 响应中的首个结算批次编号，用于管理端或商户端展示当前处理结果。
         */
        private String firstSettlementBatchNo;
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         */
        private Integer retryCount;
        /**
         * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
         */
        private String failureCode;
        /**
         * 响应中的失败说明，用于管理端或商户端展示当前处理结果。
         */
        private String failureMessage;
        /**
         * 响应中的{@code startedTime}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDateTime startedTime;
        /**
         * 响应中的{@code completedTime}，用于管理端或商户端展示当前处理结果。
         */
        private LocalDateTime completedTime;

        private Long version;

        private Boolean recoverable;
    }

    /**
     * service-admin 注入可信 Maker 后的冲正申请。
     */
    @Data
    public static class ReversalSubmitRequest {
        /**
         * 冲正创建请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 待冲正的已入账正式结算批次号。
         */
        private String originalBatchNo;
        /**
         * 页面读取的原批次 version，用于冻结前校验。
         */
        private Long expectedBatchVersion;
        /**
         * Maker 冲正申请原因，不允许为空。
         */
        private String reason;
        /**
         * service-admin 可信注入的 Maker 账户 ID。
         */
        private Long operatorId;
        /**
         * Maker 账户展示名。
         */
        private String operatorName;
        /**
         * Maker 提交时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Maker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Maker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Maker 实际提交时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信 Checker 后的冲正决策。
     */
    @Data
    public static class ReversalDecisionRequest {
        /**
         * 冲正决策请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 页面读取的冲正单 version，用于终态 CAS。
         */
        private Long expectedVersion;
        /**
         * APPROVE 或 REJECT。
         */
        private String decision;
        /**
         * Checker 决策意见，不允许为空。
         */
        private String comment;
        /**
         * service-admin 可信注入的 Checker 账户 ID。
         */
        private Long operatorId;
        /**
         * Checker 账户展示名。
         */
        private String operatorName;
        /**
         * Checker 决策时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Checker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Checker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Checker 实际决策时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 冲正command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class ReversalCommandResponse {
        /**
         * 冲正申请单号。
         */
        private String reversalOrderNo;
        /**
         * PENDING_APPROVAL、APPROVED 或 REJECTED。
         */
        private String reversalStatus;
        /**
         * 被冲正的原正式批次号。
         */
        private String originalBatchNo;
        /**
         * 批准后创建的独立反向批次号；其他状态为空。
         */
        private String reversalBatchNo;
        /**
         * 原批次所属平台商户号。
         */
        private String merchantId;
        /**
         * 原批次目标 ISO 结算币种。
         */
        private String currency;
        /**
         * 原净结果 CREDIT 或 DEBIT 方向。
         */
        private String netDirection;
        /**
         * 原净结果非负金额，单位由 currency 的 ISO exponent 决定。
         */
        private BigDecimal netAmount;
        /**
         * 冲正单当前乐观锁版本。
         */
        private Long version;
    }
}
