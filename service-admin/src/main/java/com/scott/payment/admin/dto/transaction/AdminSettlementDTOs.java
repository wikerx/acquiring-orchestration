package com.scott.payment.admin.dto.transaction;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementDTOs
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算批次管理契约；浏览器命令不允许携带操作人，应用层从登录上下文补齐。
 * @status : create
 */
public final class AdminSettlementDTOs {

    private AdminSettlementDTOs() {
    }

    /** 结算档案分页查询条件。 */
    @Data
    public static class ProfileSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算档案编号精确过滤，可空。 */
        private String settlementProfileNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 目标结算币种过滤，可空。 */
        private String targetCurrency;
        /** 自动或人工处理模式过滤，可空。 */
        private String processingMode;
        /** 结算档案状态过滤，可空。 */
        private String profileStatus;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 结算档案运营视图；账户、币种和生效区间只读，避免改变历史候选身份。 */
    @Data
    public static class ProfileSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算档案表主键。 */
        private Long id;
        /** 结算档案业务编号。 */
        private String settlementProfileNo;
        /** 商户号。 */
        private String merchantId;
        /** 商户名称。 */
        private String merchantName;
        /** 结算资金账户主键。 */
        private Long settlementAccountId;
        /** 结算资金账户号。 */
        private String settlementAccountNo;
        /** 结算资金账户状态；只有 NORMAL 账户允许后续入账。 */
        private String settlementAccountStatus;
        /** 目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 计算业务日期和日切点的 IANA 时区。 */
        private String businessTimeZone;
        /** 商户业务时区内的每日结算日切时间。 */
        private LocalTime dailyCutoffTime;
        /** 自动或人工结算处理模式。 */
        private String processingMode;
        /** 结算档案当前状态。 */
        private String profileStatus;
        /** 档案生效业务日期。 */
        private LocalDate effectiveDate;
        /** 档案失效业务日期，可空。 */
        private LocalDate expireDate;
        /** 结算档案乐观锁版本。 */
        private Long version;
        /** 创建时间。 */
        private LocalDateTime createTime;
        /** 最后更新时间。 */
        private LocalDateTime updateTime;
    }

    /** 仅允许调整后续调度行为，不允许修改账户、币种或历史有效期。 */
    @Data
    public static class ProfileUpdateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 更新后的自动或人工处理模式。 */
        private String processingMode;
        /** 更新后的 IANA 业务时区。 */
        private String businessTimeZone;
        /** 更新后的业务时区内日切时间。 */
        private LocalTime dailyCutoffTime;
        /** 客户端读取到的期望乐观锁版本。 */
        private Long expectedVersion;
    }

    /** 正式结算批次分页查询条件。 */
    @Data
    public static class BatchSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算批次号精确过滤，可空。 */
        private String settlementBatchNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 批次类型过滤，可空。 */
        private String batchType;
        /** 批次状态过滤，可空。 */
        private String batchStatus;
        /** 业务日期起点，包含。 */
        private LocalDate beginBusinessDate;
        /** 业务日期终点，包含。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
        /** 仅保留给旧版 service-settlement 内部查询客户端兼容，Admin 页面不再使用。 */
        private Long cursorId;
        /** 仅保留给旧版 service-settlement 内部查询客户端兼容，Admin 页面不再使用。 */
        private Integer limit;
    }

    /** 正式结算批次运营摘要及 Maker-Checker 审计信息。 */
    @Data
    public static class BatchSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算批次表主键。 */
        private Long id;
        /** 稳定结算批次号。 */
        private String settlementBatchNo;
        /** 面向页面展示的批次号，通常与稳定批次号一致。 */
        private String displayBatchNo;
        /** 商户业务时区下的结算业务日期。 */
        private LocalDate businessDate;
        /** 生成业务日期时使用的 IANA 时区。 */
        private String businessTimeZone;
        /** 商户当日批次序号。 */
        private Integer dailySequence;
        /** 商户号。 */
        private String merchantId;
        /** 结算档案主键。 */
        private Long settlementProfileId;
        /** 结算资金账户主键。 */
        private Long settlementAccountId;
        /** 批次唯一目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** REGULAR、RESERVE_RELEASE、ADJUSTMENT、REVERSAL 等批次类型。 */
        private String batchType;
        /** 冲正批次关联的原批次号，非冲正为空。 */
        private String originalBatchNo;
        /** 人工预审产生批次对应的预审单号，可空。 */
        private String reviewOrderNo;
        /** AUTO 或 MANUAL 创建模式。 */
        private String createMode;
        /** 批次状态。 */
        private String batchStatus;
        /** 批次结果中去重后的真实来源交易数。 */
        private Long transactionCount;
        /** 批次认领的结算项目数，包含交易清分修订或保证金动作。 */
        private Integer candidateCount;
        /** 可生成交易主单/动作单状态投影的真实 CLEARING_REVISION 候选数。 */
        private Integer projectableCandidateCount;
        /** 商户视角净入账方向。 */
        private String netDirection;
        /** 目标结算币种净入账金额。 */
        private BigDecimal netAmount;
        /** 批次结果指纹，用于重试时校验结果未漂移。 */
        private String resultFingerprint;
        /** Maker 可信 Admin 账户主键，自动批次可空。 */
        private Long makerAccountId;
        /** Maker 可信账户名称。 */
        private String makerAccountName;
        /** 人工创建或操作原因。 */
        private String makerReason;
        /** Maker 操作时间。 */
        private LocalDateTime makerTime;
        /** Checker 可信 Admin 账户主键。 */
        private Long checkerAccountId;
        /** Checker 可信账户名称。 */
        private String checkerAccountName;
        /** Checker 复核意见。 */
        private String checkerComment;
        /** Checker 操作时间。 */
        private LocalDateTime checkerTime;
        /** 批次失败后的重试次数。 */
        private Integer retryCount;
        /** 最近失败阶段。 */
        private String lastFailureStage;
        /** 最近失败码。 */
        private String lastFailureCode;
        /** 最近脱敏失败摘要。 */
        private String lastFailureMessage;
        /** 批次汇率全部锁定时间。 */
        private LocalDateTime rateLockedTime;
        /** 批次结果计算完成时间。 */
        private LocalDateTime calculatedTime;
        /** 资金成功入账时间。 */
        private LocalDateTime postedTime;
        /** 批次取消时间。 */
        private LocalDateTime cancelledTime;
        /** 批次乐观锁版本。 */
        private Long version;
        /** 批次创建时间。 */
        private LocalDateTime createTime;
        /** 批次最后更新时间。 */
        private LocalDateTime updateTime;
    }

    /** 旧版内部游标查询响应；Admin 页面使用标准分页。 */
    @Data
    public static class BatchSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 当前批次摘要集合。 */
        private List<BatchSummary> records = Collections.emptyList();
        /** 是否仍有下一页。 */
        private boolean hasMore;
        /** 下一页主键游标。 */
        private Long nextCursorId;
    }

    /** 批次内一条不可变锁定汇率。 */
    @Data
    public static class RateLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 批次汇率表主键。 */
        private Long id;
        /** 汇率源币种。 */
        private String sourceCurrency;
        /** 汇率目标币种，与批次目标币种一致。 */
        private String targetCurrency;
        /** 结算汇率类型。 */
        private String rateType;
        /** 一单位源币种对应目标币种的直接汇率。 */
        private BigDecimal directRate;
        /** 源币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 汇率来源。 */
        private String rateSource;
        /** 原始报价编号，可空。 */
        private String quoteId;
        /** 原报价是 DIRECT、INVERSE 或同币种恒等方向。 */
        private String sourceQuoteDirection;
        /** 原报价生效时间。 */
        private LocalDateTime effectiveTime;
        /** 汇率锁定到批次的时间。 */
        private LocalDateTime lockedTime;
        /** 锁定汇率的系统或可信操作人。 */
        private String lockedBy;
    }

    /** 按支付维度、结果项目、费用类别、方向和币种聚合的批次结果。 */
    @Data
    public static class ResultSummaryLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 支付类型。 */
        private String paymentType;
        /** 支付方式。 */
        private String paymentMethod;
        /** 交易类型。 */
        private String transactionType;
        /** 结算结果项目类型。 */
        private String resultItemType;
        /** 费用类别，非费用项目可空。 */
        private String feeCategory;
        /** 商户视角方向。 */
        private String direction;
        /** 清分来源币种。 */
        private String sourceCurrency;
        /** 来源币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 批次目标币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 当前分组去重后的真实交易数。 */
        private Long transactionCount;
        /** 当前分组原币种金额合计。 */
        private BigDecimal sourceAmount;
        /** 当前分组目标币种金额合计。 */
        private BigDecimal targetAmount;
    }

    /** 批次最终净入账结果行。 */
    @Data
    public static class NetPosting implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算结果表主键。 */
        private Long id;
        /** 结算结果明细号。 */
        private String settlementResultItemNo;
        /** 被冲正的原结果主键，非冲正为空。 */
        private Long reversalOfResultItemId;
        /** 商户视角净入账方向。 */
        private String direction;
        /** 目标结算币种净额。 */
        private BigDecimal targetAmount;
        /** 批次目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 对应资金流水的唯一幂等键。 */
        private String ledgerIdempotencyKey;
        /** 净额聚合公式快照。 */
        private String formulaSnapshot;
        /** 结果明细创建时间。 */
        private LocalDateTime createTime;
    }

    /** 批次投影任务和可靠 Outbox 的运维计数。 */
    @Data
    public static class OperationalState implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 投影任务总数，仅真实 CLEARING_REVISION 候选会生成交易投影。 */
        private Long projectionTaskCount;
        /** 已完成投影任务数。 */
        private Long projectionCompletedCount;
        /** 失败待补偿投影任务数。 */
        private Long projectionFailedCount;
        /** 批次 Outbox 事件总数。 */
        private Long outboxEventCount;
        /** 已发送 Outbox 事件数。 */
        private Long outboxSentCount;
        /** 发送失败待重试 Outbox 事件数。 */
        private Long outboxFailedCount;
    }

    /** 正式结算批次详情。 */
    @Data
    public static class BatchDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 批次运营摘要。 */
        private BatchSummary batch;
        /** 批次不可变锁定汇率。 */
        private List<RateLine> rates = Collections.emptyList();
        /** 批次结果维度汇总。 */
        private List<ResultSummaryLine> resultSummaries = Collections.emptyList();
        /** 批次最终净入账结果。 */
        private NetPosting netPosting;
        /** 投影和 Outbox 运维计数。 */
        private OperationalState operationalState;
    }

    /** 浏览器命令不包含 operator，避免伪造操作人。 */
    @Data
    public static class BatchCommandRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 客户端生成的幂等请求键。 */
        private String requestKey;
        /** 批次期望乐观锁版本。 */
        private Long expectedVersion;
        /** 人工取消原因。 */
        private String reason;
    }

    /** service-admin 补齐可信操作人后的内部命令。 */
    @Data
    public static class InternalBatchCommandRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 客户端生成的幂等请求键。 */
        private String requestKey;
        /** 批次期望乐观锁版本。 */
        private Long expectedVersion;
        /** 人工取消原因。 */
        private String reason;
        /** 可信 Admin 操作人主键。 */
        private Long operatorId;
        /** 可信 Admin 操作人名称。 */
        private String operatorName;
        /** 操作时已认证角色快照。 */
        private String roleSnapshot;
        /** 可信客户端地址。 */
        private String clientIp;
        /** 客户端 User-Agent，长度受限。 */
        private String userAgent;
        /** Admin 操作时间。 */
        private LocalDateTime operationTime;
    }

    /** 批次取消或其他控制命令结果。 */
    @Data
    public static class BatchCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 原请求批次号。 */
        private String settlementBatchNo;
        /** 命令产生的结果批次号，可空。 */
        private String resultBatchNo;
        /** 幂等命令结果状态。 */
        private String resultStatus;
        /** 取消后释放回 READY 的候选数量。 */
        private Integer releasedCandidateCount;
    }

    /** 交易或保证金结算候选分页查询条件。 */
    @Data
    public static class CandidateSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 候选编号精确过滤，可空。 */
        private String candidateNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 来源真实平台交易号过滤，可空。 */
        private String sourceTransactionId;
        /** 商户订单号过滤，可空。 */
        private String merchantOrderNo;
        /** 来源交易时间起点，包含。 */
        private LocalDateTime beginTransactionTime;
        /** 来源交易时间终点，不包含。 */
        private LocalDateTime endTransactionTime;
        /** 支付类型过滤，可空。 */
        private String paymentType;
        /** 支付方式过滤，可空。 */
        private String paymentMethod;
        /** 交易类型过滤，可空。 */
        private String transactionType;
        /** 标签币种过滤，可空。 */
        private String labelCurrency;
        /** 目标结算币种过滤，可空。 */
        private String targetCurrency;
        /** 来源清分修订号过滤，可空。 */
        private Integer sourceRevision;
        /** 保证金责任编号过滤，可空。 */
        private String reserveNo;
        /** 保证金责任状态过滤，可空。 */
        private String reserveStatus;
        /** 预计释放日期起点，包含。 */
        private LocalDate beginExpectedReleaseDate;
        /** 预计释放日期终点，包含。 */
        private LocalDate endExpectedReleaseDate;
        /** 是否只查询已到期保证金候选。 */
        private Boolean due;
        /** 是否只查询 FROZEN 保证金责任。 */
        private Boolean frozen;
        /** 保证金剩余责任最小值，原标签币种。 */
        private BigDecimal minRemainingAmount;
        /** 保证金剩余责任最大值，原标签币种。 */
        private BigDecimal maxRemainingAmount;
        /** 候选状态过滤，可空。 */
        private String candidateStatus;
        /** 可结算业务日期起点，包含。 */
        private LocalDate beginEligibleDate;
        /** 可结算业务日期终点，包含。 */
        private LocalDate endEligibleDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 交易清分修订或保证金动作结算候选摘要。 */
    @Data
    public static class CandidateSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 候选表主键。 */
        private Long id;
        /** 候选业务编号。 */
        private String candidateNo;
        /** CLEARING_REVISION、RESERVE_RELEASE 或 ADJUSTMENT。 */
        private String sourceType;
        /** 来源清分状态或保证金明细业务编号。 */
        private String sourceBusinessId;
        /** 来源清分修订号。 */
        private Integer sourceRevision;
        /** 真实来源平台交易号；保证金候选回溯原支付交易。 */
        private String sourceTransactionId;
        /** 来源交易时间，用于物理季度定位。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 商户号。 */
        private String merchantId;
        /** 商户名称。 */
        private String merchantName;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 支付类型。 */
        private String paymentType;
        /** 支付方式。 */
        private String paymentMethod;
        /** 交易类型。 */
        private String transactionType;
        /** 来源标签币种。 */
        private String labelCurrency;
        /** 来源标签金额。 */
        private BigDecimal labelAmount;
        /** 标签币种 exponent。 */
        private Integer labelCurrencyExponent;
        /** 参与本金结算的标签币种毛额。 */
        private BigDecimal grossLabelAmount;
        /** 清分平台费用组件合计；可能跨标签币种和 USD，页面不得自行相加换汇。 */
        private BigDecimal platformFeeAmount;
        /** 原标签币种保证金扣留金额。 */
        private BigDecimal reserveAmount;
        /** 清分阶段可确定的净结算金额，跨币种费用待结算求值时可空。 */
        private BigDecimal netSettlementAmount;
        /** 费用是否已在清分阶段最终求值。 */
        private String feeEvaluationStatus;
        /** 保证金候选动作类型。 */
        private String reserveActionType;
        /** 保证金责任方向。 */
        private String reserveDirection;
        /** 当前保证金动作金额，使用原标签币种。 */
        private BigDecimal reserveActionAmount;
        /** 保证金责任编号。 */
        private String reserveNo;
        /** 保证金责任状态。 */
        private String reserveStatus;
        /** 保证金预计释放业务日期。 */
        private LocalDate expectedReserveReleaseDate;
        /** 保证金当前剩余责任，使用原标签币种。 */
        private BigDecimal remainingAmount;
        /** 结算档案主键。 */
        private Long settlementProfileId;
        /** 目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 最早可结算业务日期。 */
        private LocalDate settlementEligibleDate;
        /** 候选状态。 */
        private String candidateStatus;
        /** 已认领或已入账批次号，可空。 */
        private String settlementBatchNo;
        /** 已锁定预审单号，可空。 */
        private String reviewOrderNo;
        /** 影子候选标记；正式查询只应返回 0。 */
        private Integer shadowMode;
        /** 候选被批次或预审认领时间。 */
        private LocalDateTime claimedTime;
        /** 候选随批次资金入账时间。 */
        private LocalDateTime postedTime;
        /** 候选乐观锁版本。 */
        private Long version;
        /** 候选创建时间。 */
        private LocalDateTime createTime;
        /** 候选最后更新时间。 */
        private LocalDateTime updateTime;
    }

    /** 不可变结算结果明细分页查询条件。 */
    @Data
    public static class ResultItemSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算批次号过滤，可空。 */
        private String settlementBatchNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 来源真实平台交易号过滤，可空。 */
        private String sourceTransactionId;
        /** 结果项目类型过滤，可空。 */
        private String resultItemType;
        /** TRACE、FINANCIAL_COMPONENT 或 LEDGER_POSTING。 */
        private String resultRole;
        /** 商户视角方向过滤，可空。 */
        private String direction;
        /** 目标结算币种过滤，可空。 */
        private String targetCurrency;
        /** TRANSACTION_CLEARING 或 RESERVE_CLEARING，用于交易与保证金结果分区。 */
        private String sourceDetailType;
        /** 批次业务日期起点，包含。 */
        private LocalDate beginBusinessDate;
        /** 批次业务日期终点，包含。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 不可变结算结果行；金额和汇率仅按数据库精度透传，不在 Admin 二次计算。 */
    @Data
    public static class ResultItemSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算结果表主键。 */
        private Long id;
        /** 结算结果明细号。 */
        private String settlementResultItemNo;
        /** 所属结算批次号。 */
        private String settlementBatchNo;
        /** 来源候选主键。 */
        private Long candidateId;
        /** 批次内稳定结果行号。 */
        private Integer resultLineNo;
        /** 商户号。 */
        private String merchantId;
        /** 结算资金账户主键。 */
        private Long settlementAccountId;
        /** TRANSACTION_CLEARING 或 RESERVE_CLEARING。 */
        private String sourceDetailType;
        /** 来源清分或保证金明细编号。 */
        private String sourceDetailNo;
        /** 被冲正的原结果主键，非冲正为空。 */
        private Long reversalOfResultItemId;
        /** 真实来源平台交易号，纯保证金结果可回溯原支付。 */
        private String sourceTransactionId;
        /** 来源交易时间。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 费用逻辑组号，非费用项目为空。 */
        private String feeGroupNo;
        /** 本金、费用组件、费用组最终值、调整、冲正或净入账等项目类型。 */
        private String resultItemType;
        /** 结果行财务角色。 */
        private String resultRole;
        /** 支付类型。 */
        private String paymentType;
        /** 支付方式。 */
        private String paymentMethod;
        /** 交易类型。 */
        private String transactionType;
        /** 费用类别，非费用项目为空。 */
        private String feeCategory;
        /** 商户视角方向。 */
        private String direction;
        /** 清分保存的原币种金额。 */
        private BigDecimal sourceAmount;
        /** 清分保存的原币种。 */
        private String sourceCurrency;
        /** 原币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 使用的批次锁定汇率主键。 */
        private Long settlementBatchRateId;
        /** 一单位原币种对应目标币种的批次直接汇率。 */
        private BigDecimal directRate;
        /** DECIMAL128 计算后的未舍入目标币种金额。 */
        private BigDecimal unroundedTargetAmount;
        /** 按目标币种 exponent 最终舍入后的金额。 */
        private BigDecimal targetAmount;
        /** 批次目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 费用组命中的最低或最高费用边界。 */
        private String appliedLimit;
        /** USD 最低费换算后的未舍入目标币种值，可空。 */
        private BigDecimal minimumTargetAmount;
        /** USD 最高费换算后的未舍入目标币种值，可空。 */
        private BigDecimal maximumTargetAmount;
        /** 最终目标金额舍入模式。 */
        private String roundingMode;
        /** 金额计算公式快照。 */
        private String formulaSnapshot;
        /** 对应资金流水唯一幂等键。 */
        private String ledgerIdempotencyKey;
        /** 批次业务日期。 */
        private LocalDate businessDate;
        /** 结算结果创建时间。 */
        private LocalDateTime createTime;
    }

    /** 结算资金流水分页查询条件。 */
    @Data
    public static class PostingSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算批次号过滤，可空。 */
        private String settlementBatchNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 资金流水号过滤，可空。 */
        private String ledgerNo;
        /** 账户方向过滤，可空。 */
        private String direction;
        /** AUTO 或 MANUAL 操作模式过滤，可空。 */
        private String operationMode;
        /** 流水币种过滤，可空。 */
        private String currency;
        /** 入账时间起点，包含。 */
        private LocalDateTime beginPostedTime;
        /** 入账时间终点，包含。 */
        private LocalDateTime endPostedTime;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 结算净额余额流水；发生额非负，方向单独表达借贷。 */
    @Data
    public static class PostingSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 资金流水表主键。 */
        private Long id;
        /** 不可变资金流水号。 */
        private String ledgerNo;
        /** 同一资金操作的流水组号。 */
        private String ledgerGroupNo;
        /** 资金账户主键。 */
        private Long accountId;
        /** 商户号。 */
        private String merchantId;
        /** 结算入账或冲正业务类型。 */
        private String businessType;
        /** 资金流水摘要。 */
        private String summary;
        /** 来源业务编号。 */
        private String businessNo;
        /** 结算批次号。 */
        private String settlementBatchNo;
        /** 账户和流水币种。 */
        private String currency;
        /** 币种 exponent。 */
        private Integer currencyExponent;
        /** 账户金额方向。 */
        private String direction;
        /** 非负发生额。 */
        private BigDecimal amount;
        /** 入账前余额。 */
        private BigDecimal balanceBefore;
        /** 入账后余额。 */
        private BigDecimal balanceAfter;
        /** 账户级单调递增序列。 */
        private Long accountSequence;
        /** AUTO 或 MANUAL。 */
        private String operationMode;
        /** 可信操作人账户主键。 */
        private Long operatorId;
        /** 可信操作人名称。 */
        private String operatorName;
        /** Maker-Checker 复核人账户主键，可空。 */
        private Long reviewerId;
        /** Maker-Checker 复核人名称，可空。 */
        private String reviewerName;
        /** 人工操作原因。 */
        private String operationReason;
        /** 复核意见。 */
        private String reviewComment;
        /** 业务发生时间。 */
        private LocalDateTime businessTime;
        /** 人工申请提交时间。 */
        private LocalDateTime submitTime;
        /** 人工复核时间。 */
        private LocalDateTime reviewTime;
        /** 资金成功入账时间。 */
        private LocalDateTime postedTime;
        /** 外部或内部请求编号。 */
        private String requestId;
        /** 资金流水唯一幂等键。 */
        private String idempotencyKey;
        /** 被当前流水冲正的原流水主键。 */
        private Long reversalOfLedgerId;
        /** 流水创建时间。 */
        private LocalDateTime createTime;
    }

    /** 保证金动作分页查询条件。 */
    @Data
    public static class ReserveItemSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算批次号过滤，可空。 */
        private String settlementBatchNo;
        /** 商户号过滤，可空且必须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 保证金责任编号过滤，可空。 */
        private String reserveNo;
        /** 来源真实平台交易号过滤，可空。 */
        private String sourceTransactionId;
        /** 保证金动作类型过滤，可空。 */
        private String actionType;
        /** 保证金原标签币种过滤，可空。 */
        private String currency;
        /** 批次业务日期起点，包含。 */
        private LocalDate beginBusinessDate;
        /** 批次业务日期终点，包含。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** Admin 保证金不可变动作和当前资金责任，币种始终为原标签币种。 */
    @Data
    public static class ReserveItemSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 保证金动作表主键。 */
        private Long actionId;
        /** 不可变保证金动作编号。 */
        private String reserveActionNo;
        /** 保证金责任表主键。 */
        private Long reserveItemId;
        /** 保证金责任编号。 */
        private String reserveNo;
        /** 动作资金化所属结算批次号。 */
        private String settlementBatchNo;
        /** 批次业务日期。 */
        private LocalDate businessDate;
        /** 商户号。 */
        private String merchantId;
        /** 保证金资金账户主键。 */
        private Long accountId;
        /** 来源真实平台交易号。 */
        private String sourceTransactionId;
        /** 来源交易时间。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 来源保证金业务编号。 */
        private String sourceBusinessNo;
        /** 来源保证金清分明细号。 */
        private String sourceReserveDetailNo;
        /** HOLD、RETURN、RELEASE、ADJUSTMENT 或对应冲正动作。 */
        private String actionType;
        /** 保证金责任方向。 */
        private String direction;
        /** 保证金原标签币种，不参与汇率转换。 */
        private String currency;
        /** 保证金币种 exponent。 */
        private Integer currencyExponent;
        /** 当前不可变动作金额。 */
        private BigDecimal amount;
        /** 累计扣留金额。 */
        private BigDecimal retainedAmount;
        /** 累计退款返还金额。 */
        private BigDecimal returnedAmount;
        /** 累计到期释放金额。 */
        private BigDecimal releasedAmount;
        /** 累计增加责任的借方调整金额。 */
        private BigDecimal debitAdjustmentAmount;
        /** 累计减少责任的贷方调整金额。 */
        private BigDecimal creditAdjustmentAmount;
        /** 累计冲正金额。 */
        private BigDecimal reversedAmount;
        /** 当前责任：扣留 + 借方调整 - 返还 - 释放 - 贷方调整 - 冲正。 */
        private BigDecimal remainingAmount;
        /** 保证金责任状态。 */
        private String reserveStatus;
        /** 预计可释放业务日期。 */
        private LocalDate expectedReleaseDate;
        /** 当前动作发生时间。 */
        private LocalDateTime actionTime;
    }

    /** Admin 结算预审分页查询条件，业务日期按档案时区解释。 */
    @Data
    public static class ReviewSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预审单号精确过滤，可空。 */
        private String reviewOrderNo;
        /** 商户号精确过滤，查询层仍须叠加 Admin 数据范围。 */
        private String merchantId;
        /** 预审类型过滤，可空。 */
        private String reviewType;
        /** Maker-Checker 预审状态过滤，可空。 */
        private String reviewStatus;
        /** 业务日期起点，包含。 */
        private LocalDate beginBusinessDate;
        /** 业务日期终点，包含。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 结算预审主单摘要，包含提交、复核和正式批次关联审计。 */
    @Data
    public static class ReviewSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预审主单数据库主键。 */
        private Long id;
        /** 对外稳定的预审单号。 */
        private String reviewOrderNo;
        /** 预审业务类型。 */
        private String reviewType;
        /** 预审创建模式，例如人工创建。 */
        private String createMode;
        /** 预审所属商户号。 */
        private String merchantId;
        /** 结算档案主键快照。 */
        private Long settlementProfileId;
        /** 目标结算资金账户主键快照。 */
        private Long settlementAccountId;
        /** 批次目标结算币种。 */
        private String targetCurrency;
        /** 目标结算币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 按结算档案时区确定的业务日期。 */
        private LocalDate businessDate;
        /** 业务日期计算所用 IANA 时区快照。 */
        private String businessTimeZone;
        /** 本次预审锁定的全部候选数。 */
        private Integer candidateCount;
        /** 真实 CLEARING_REVISION 交易投影候选数。 */
        private Integer projectableCandidateCount;
        /** 预审净额方向。 */
        private String netDirection;
        /** 目标币种预审净额，精度由 targetCurrencyExponent 解释。 */
        private BigDecimal netAmount;
        /** Maker-Checker 预审状态。 */
        private String reviewStatus;
        /** 可信 Maker 管理账号主键。 */
        private Long submittedByAccountId;
        /** 可信 Maker 管理账号名称快照。 */
        private String submittedByAccountName;
        /** Maker 提交原因。 */
        private String submitReason;
        /** Maker 提交时间。 */
        private LocalDateTime submittedTime;
        /** 可信 Checker 管理账号主键。 */
        private Long decidedByAccountId;
        /** 可信 Checker 管理账号名称快照。 */
        private String decidedByAccountName;
        /** Checker 审批或拒绝动作。 */
        private String decisionAction;
        /** Checker 决策意见。 */
        private String reviewComment;
        /** Checker 决策时间。 */
        private LocalDateTime decisionTime;
        /** 审批后生成的正式结算批次号，可空。 */
        private String settlementBatchNo;
        /** 预审主单乐观锁版本。 */
        private Long version;
        /** 预审主单创建时间。 */
        private LocalDateTime createTime;
        /** 预审主单最后更新时间。 */
        private LocalDateTime updateTime;
    }

    /** 预审与结算候选的锁定关系，记录消费或释放结果。 */
    @Data
    public static class ReviewCandidateLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预审候选关系编号。 */
        private String reviewCandidateNo;
        /** 原结算候选数据库主键。 */
        private Long candidateId;
        /** 原结算候选业务编号。 */
        private String candidateNo;
        /** CLEARING_REVISION、RESERVE_RELEASE 或 RESERVE_ADJUSTMENT。 */
        private String sourceType;
        /** 来源业务幂等标识。 */
        private String sourceBusinessId;
        /** 来源事实修订版本。 */
        private Integer sourceRevision;
        /** 真实交易来源的平台交易号；纯保证金候选可空。 */
        private String sourceTransactionId;
        /** 来源交易时间；纯保证金候选可空。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 候选在预审中的 LOCKED、CONSUMED 或 RELEASED 状态。 */
        private String relationStatus;
        /** 候选被预审锁定时间。 */
        private LocalDateTime lockedTime;
        /** 候选被正式批次消费时间，可空。 */
        private LocalDateTime consumedTime;
        /** 预审拒绝后释放候选时间，可空。 */
        private LocalDateTime releasedTime;
    }

    /** 预审期间锁定的统一直接汇率快照。 */
    @Data
    public static class ReviewRateLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 换汇源币种。 */
        private String sourceCurrency;
        /** 换汇目标结算币种。 */
        private String targetCurrency;
        /** 一单位源币种可兑换的目标币种数量。 */
        private BigDecimal directRate;
        /** 源币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 汇率提供方或汇率策略来源。 */
        private String rateSource;
        /** 汇率报价唯一标识。 */
        private String quoteId;
        /** 上游原始报价方向，用于审计直接或倒数归一。 */
        private String sourceQuoteDirection;
        /** 汇率报价生效时间。 */
        private LocalDateTime effectiveTime;
        /** 汇率在预审单中锁定时间。 */
        private LocalDateTime lockedTime;
    }

    /** 预审详情，组合主单、候选、锁定汇率和目标币种汇总。 */
    @Data
    public static class ReviewDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预审主单摘要。 */
        private ReviewSummary review;
        /** 预审锁定候选明细。 */
        private List<ReviewCandidateLine> candidates = Collections.emptyList();
        /** 预审锁定汇率明细。 */
        private List<ReviewRateLine> rates = Collections.emptyList();
        /** 按目标币种和结果类型聚合的试算汇总。 */
        private List<ResultSummaryLine> summaries = Collections.emptyList();
    }

    /** 浏览器提交的候选引用，以版本号保护候选并发状态。 */
    @Data
    public static class ReviewCandidateReference implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 结算候选数据库主键。 */
        private Long candidateId;
        /** 页面读取到的候选乐观锁版本。 */
        private Long expectedVersion;
    }

    /** 浏览器预审提交请求，不接受操作人字段。 */
    @Data
    public static class ReviewSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Maker 请求幂等键。 */
        private String requestKey;
        /** 预审业务类型。 */
        private String reviewType;
        /** 按结算档案时区确定的业务日期。 */
        private LocalDate businessDate;
        /** 候选交易时间窗口起点，包含。 */
        private LocalDateTime cutoffBeginTime;
        /** 候选交易时间窗口终点，不包含。 */
        private LocalDateTime cutoffEndTime;
        /** 待锁定候选及其预期版本。 */
        private List<ReviewCandidateReference> candidates = Collections.emptyList();
        /** Maker 提交原因。 */
        private String reason;
    }

    /** 浏览器预审决策请求，不接受 Checker 字段。 */
    @Data
    public static class ReviewDecisionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Checker 请求幂等键。 */
        private String requestKey;
        /** 页面读取到的预审主单乐观锁版本。 */
        private Long expectedVersion;
        /** Checker 决策意见。 */
        private String comment;
    }

    /** service-admin 注入可信 Maker 后发送给 service-settlement 的预审命令。 */
    @Data
    public static class InternalReviewSubmitRequest extends ReviewSubmitRequest {
        private static final long serialVersionUID = 1L;
        /** 可信登录管理账号主键。 */
        private Long operatorId;
        /** 可信登录管理账号名称快照。 */
        private String operatorName;
        /** 提交时的角色与权限快照。 */
        private String roleSnapshot;
        /** 操作来源客户端 IP。 */
        private String clientIp;
        /** 操作来源 User-Agent。 */
        private String userAgent;
        /** service-admin 记录的操作时间。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信 Checker 后发送给 service-settlement 的预审决策。 */
    @Data
    public static class InternalReviewDecisionRequest extends ReviewDecisionRequest {
        private static final long serialVersionUID = 1L;
        /** APPROVE 或 REJECT 决策。 */
        private String decision;
        /** 可信登录管理账号主键。 */
        private Long operatorId;
        /** 可信登录管理账号名称快照。 */
        private String operatorName;
        /** 决策时的角色与权限快照。 */
        private String roleSnapshot;
        /** 操作来源客户端 IP。 */
        private String clientIp;
        /** 操作来源 User-Agent。 */
        private String userAgent;
        /** service-admin 记录的操作时间。 */
        private LocalDateTime operationTime;
    }

    /** 预审提交或决策后的最新主单结果。 */
    @Data
    public static class ReviewCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预审单号。 */
        private String reviewOrderNo;
        /** 命令执行后的预审状态。 */
        private String reviewStatus;
        /** 审批生成的正式结算批次号，可空。 */
        private String settlementBatchNo;
        /** 预审锁定候选数。 */
        private Integer candidateCount;
        /** 目标结算币种。 */
        private String targetCurrency;
        /** 目标结算币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 预审净额方向。 */
        private String netDirection;
        /** 目标币种预审净额。 */
        private BigDecimal netAmount;
        /** 命令执行后的乐观锁版本。 */
        private Long version;
    }

    /** Admin 结算冲正单分页查询条件。 */
    @Data
    public static class ReversalSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 冲正单号精确过滤，可空。 */
        private String reversalOrderNo;
        /** 原正式结算批次号精确过滤，可空。 */
        private String originalBatchNo;
        /** 商户号精确过滤，查询层仍须叠加 Admin 数据范围。 */
        private String merchantId;
        /** Maker-Checker 冲正状态过滤，可空。 */
        private String reversalStatus;
        /** 提交日期起点，包含。 */
        private LocalDate beginSubmittedDate;
        /** 提交日期终点，包含。 */
        private LocalDate endSubmittedDate;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受查询预算限制。 */
        private Integer pageSize;
    }

    /** 结算冲正主单摘要，保存原批次、逆向批次及双人复核审计。 */
    @Data
    public static class ReversalSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 冲正主单数据库主键。 */
        private Long id;
        /** 对外稳定的冲正单号。 */
        private String reversalOrderNo;
        /** 被冲正的原正式结算批次号。 */
        private String originalBatchNo;
        /** 审批后生成的逆向结算批次号，可空。 */
        private String reversalBatchNo;
        /** 冲正所属商户号。 */
        private String merchantId;
        /** 原批次入账资金账户主键。 */
        private Long settlementAccountId;
        /** 原批次目标结算币种。 */
        private String targetCurrency;
        /** 目标结算币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 原批次净额方向。 */
        private String netDirection;
        /** 原批次目标币种净额。 */
        private BigDecimal netAmount;
        /** Maker-Checker 冲正状态。 */
        private String reversalStatus;
        /** 可信 Maker 管理账号主键。 */
        private Long submittedByAccountId;
        /** 可信 Maker 管理账号名称快照。 */
        private String submittedByAccountName;
        /** Maker 冲正申请原因。 */
        private String submitReason;
        /** Maker 提交时间。 */
        private LocalDateTime submittedTime;
        /** 可信 Checker 管理账号主键。 */
        private Long decidedByAccountId;
        /** 可信 Checker 管理账号名称快照。 */
        private String decidedByAccountName;
        /** Checker 审批或拒绝动作。 */
        private String decisionAction;
        /** Checker 决策意见。 */
        private String decisionComment;
        /** Checker 决策时间。 */
        private LocalDateTime decisionTime;
        /** 冲正主单乐观锁版本。 */
        private Long version;
        /** 冲正主单创建时间。 */
        private LocalDateTime createTime;
        /** 冲正主单最后更新时间。 */
        private LocalDateTime updateTime;
    }

    /** 冲正详情，补充原批次指纹、资金关联和操作环境审计快照。 */
    @Data
    public static class ReversalDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 冲正主单摘要。 */
        private ReversalSummary reversal;
        /** 原批次关键事实指纹，用于审批前后防篡改校验。 */
        private String sourceFingerprint;
        /** 申请时读取到的原批次乐观锁版本。 */
        private Long originalBatchVersion;
        /** 原批次净入账结果明细主键。 */
        private Long originalNetResultItemId;
        /** 原批次资金流水主键。 */
        private Long originalFundLedgerId;
        /** Maker 提交时角色与权限快照。 */
        private String submittedRoleSnapshot;
        /** Maker 客户端 IP。 */
        private String submitClientIp;
        /** Maker User-Agent。 */
        private String submitUserAgent;
        /** Checker 决策时角色与权限快照。 */
        private String decidedRoleSnapshot;
        /** Checker 客户端 IP。 */
        private String decisionClientIp;
        /** Checker User-Agent。 */
        private String decisionUserAgent;
    }

    /** 浏览器冲正申请不接受 Maker 身份字段。 */
    @Data
    public static class ReversalSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Maker 请求幂等键。 */
        private String requestKey;
        /** 被冲正的原正式结算批次号。 */
        private String originalBatchNo;
        /** 页面读取到的原批次乐观锁版本。 */
        private Long expectedBatchVersion;
        /** Maker 冲正申请原因。 */
        private String reason;
    }

    /** 浏览器冲正决策不接受 Checker 身份字段。 */
    @Data
    public static class ReversalDecisionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Checker 请求幂等键。 */
        private String requestKey;
        /** 页面读取到的冲正主单乐观锁版本。 */
        private Long expectedVersion;
        /** Checker 决策意见。 */
        private String comment;
    }

    /** service-admin 注入可信 Maker 后发送给 service-settlement 的冲正申请。 */
    @Data
    public static class InternalReversalSubmitRequest extends ReversalSubmitRequest {
        private static final long serialVersionUID = 1L;
        /** 可信登录管理账号主键。 */
        private Long operatorId;
        /** 可信登录管理账号名称快照。 */
        private String operatorName;
        /** 提交时的角色与权限快照。 */
        private String roleSnapshot;
        /** 操作来源客户端 IP。 */
        private String clientIp;
        /** 操作来源 User-Agent。 */
        private String userAgent;
        /** service-admin 记录的操作时间。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信 Checker 后发送给 service-settlement 的冲正决策。 */
    @Data
    public static class InternalReversalDecisionRequest extends ReversalDecisionRequest {
        private static final long serialVersionUID = 1L;
        /** APPROVE 或 REJECT 决策。 */
        private String decision;
        /** 可信登录管理账号主键。 */
        private Long operatorId;
        /** 可信登录管理账号名称快照。 */
        private String operatorName;
        /** 决策时的角色与权限快照。 */
        private String roleSnapshot;
        /** 操作来源客户端 IP。 */
        private String clientIp;
        /** 操作来源 User-Agent。 */
        private String userAgent;
        /** service-admin 记录的操作时间。 */
        private LocalDateTime operationTime;
    }

    /** 冲正申请或决策后的最新主单结果。 */
    @Data
    public static class ReversalCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 冲正单号。 */
        private String reversalOrderNo;
        /** 命令执行后的冲正状态。 */
        private String reversalStatus;
        /** 被冲正的原正式结算批次号。 */
        private String originalBatchNo;
        /** 审批生成的逆向结算批次号，可空。 */
        private String reversalBatchNo;
        /** 冲正所属商户号。 */
        private String merchantId;
        /** 原批次目标结算币种。 */
        private String currency;
        /** 原批次净额方向。 */
        private String netDirection;
        /** 原批次目标币种净额。 */
        private BigDecimal netAmount;
        /** 命令执行后的乐观锁版本。 */
        private Long version;
    }
}
