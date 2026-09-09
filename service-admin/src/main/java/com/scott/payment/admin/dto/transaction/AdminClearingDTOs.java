package com.scott.payment.admin.dto.transaction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminClearingDTOs
 * @date : 2026-09-01 23:05
 * @email : scott_x@163.com
 * @description : Admin 清分查询和人工命令契约；交易费用与保证金明细保持独立，浏览器模型不得携带可信操作人。
 * @status : update
 */
public final class AdminClearingDTOs {
    private AdminClearingDTOs() {
    }

    /** Admin 清分记录分页查询条件。 */
    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 商户号过滤，可空且必须叠加当前 Admin 数据范围。 */
        private String merchantId;
        /** 平台交易号精确过滤，可空。 */
        private String transactionId;
        /** 清分状态过滤，可空。 */
        private String clearingStatus;
        /** 交易时间起点，包含，用于限制逻辑分片范围。 */
        private LocalDateTime beginTime;
        /** 交易时间终点，不包含。 */
        private LocalDateTime endTime;
        /** 页码，从 1 开始。 */
        private Integer pageNo;
        /** 页大小，受交易逻辑数据源预算限制。 */
        private Integer pageSize;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private LocalDateTime cursorTransactionDateTime;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private Long cursorId;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private Integer limit;
    }

    /** 清分记录列表摘要及 CAS 版本事实。 */
    @Data
    public static class Summary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 清分财务状态表主键。 */
        private Long id;
        /** 清分财务状态业务编号。 */
        private String financeStateId;
        /** 当前交易动作对应的平台交易号。 */
        private String transactionId;
        /** 当前交易动作号。 */
        private String operationId;
        /** 商户号。 */
        private String merchantId;
        /** 退款等派生动作对应的原交易号，可空。 */
        private String sourceTransactionId;
        /** 交易动作类型。 */
        private String transactionType;
        /** 交易动作标签币种。 */
        private String labelCurrency;
        /** 交易动作原始标签金额，与是否产生结算本金无关。 */
        private BigDecimal labelAmount;
        /** 当前清分状态。 */
        private String clearingStatus;
        /** 已生效清分修订号，从 1 开始。 */
        private Integer clearingRevision;
        /** 清分自动重试次数。 */
        private Integer clearingRetryCount;
        /** 下一次允许重试时间，可空。 */
        private LocalDateTime nextRetryTime;
        /** 最近失败码，成功时可空。 */
        private String lastFailureCode;
        /** 最近脱敏失败摘要，成功时可空。 */
        private String lastFailureMessage;
        /** 本次清分冻结的费用方案主键。 */
        private Long feePlanId;
        /** 本次清分冻结的费用版本主键。 */
        private Long feePlanVersionId;
        /** 面向运营展示的费用版本号。 */
        private Integer feePlanVersionNo;
        /** 参与本金结算的标签币种毛额。 */
        private BigDecimal grossLabelAmount;
        /** 费用是否已在清分阶段最终求值。 */
        private String feeEvaluationStatus;
        /** 结算状态；已结算事实禁止清分重算。 */
        private String settlementStatus;
        /** 商户目标结算币种。 */
        private String settlementCurrency;
        /** 最早可结算业务日期。 */
        private LocalDate settlementEligibleDate;
        /** 当前修订平台费用组件合计，保持各组件业务币种口径。 */
        private BigDecimal platformFeeAmount;
        /** 当前修订费用返还合计。 */
        private BigDecimal feeReversalAmount;
        /** 当前修订保证金扣留合计，使用原标签币种。 */
        private BigDecimal reserveAmount;
        /** 当前修订保证金返还或冲正合计。 */
        private BigDecimal reserveReversalAmount;
        /** 保证金预计释放业务日期。 */
        private LocalDate expectedReserveReleaseDate;
        /** 交易业务时间，用于物理季度定位。 */
        private LocalDateTime transactionDateTime;
        /** 交易 UTC 时间。 */
        private LocalDateTime transactionUtcTime;
        /** 交易业务时区。 */
        private String transactionTimeZone;
        /** 清分财务状态乐观锁版本。 */
        private Integer version;
    }

    /** 旧版内部游标查询响应；Admin 页面分页不使用该游标。 */
    @Data
    public static class SearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 当前页清分摘要。 */
        private List<Summary> records = Collections.emptyList();
        /** 是否仍有下一页。 */
        private boolean hasMore;
        /** 下一页复合游标交易时间。 */
        private LocalDateTime nextCursorTransactionDateTime;
        /** 下一页复合游标主键。 */
        private Long nextCursorId;
    }

    /** 一条不可变交易费用或本金清分明细。 */
    @Data
    public static class TransactionLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 清分明细编号。 */
        private String clearingDetailNo;
        /** 所属清分修订号。 */
        private Integer clearingRevision;
        /** 当前修订内稳定行号。 */
        private Integer lineNo;
        /** 本金、费用或费用返还等项目类型。 */
        private String itemType;
        /** 费用类别，非费用项目可空。 */
        private String feeCategory;
        /** 风控服务类型，可空。 */
        private String riskServiceType;
        /** 项目代码。 */
        private String itemCode;
        /** 项目显示名称。 */
        private String itemName;
        /** 商户应结金额方向。 */
        private String direction;
        /** 交易动作标签币种。 */
        private String labelCurrency;
        /** 交易动作标签金额。 */
        private BigDecimal labelAmount;
        /** 百分比、固定费或限额调整等组件类型。 */
        private String componentType;
        /** 百分比计算基数币种，非百分比组件可空。 */
        private String basisCurrency;
        /** 百分比计算基数金额，非百分比组件可空。 */
        private BigDecimal basisAmount;
        /** 当前明细金额。 */
        private BigDecimal amount;
        /** 当前明细币种；百分比跟随标签币种，固定费和限额保持 USD。 */
        private String currency;
        /** 当前明细币种 exponent。 */
        private Integer currencyExponent;
        /** 百分比数值，2.3 表示 2.3%，非百分比组件可空。 */
        private BigDecimal percentageRate;
        /** USD 固定单笔费事实，可空。 */
        private BigDecimal fixedAmountUsd;
        /** USD 最低费用事实，可空。 */
        private BigDecimal minimumAmountUsd;
        /** USD 最高费用事实，可空。 */
        private BigDecimal maximumAmountUsd;
        /** 最低/最高费是否已求值。 */
        private String limitEvaluationStatus;
        /** 实际命中的最低或最高费用边界。 */
        private String appliedLimit;
        /** 生成该明细的规则公式快照。 */
        private String formulaSnapshot;
        /** 不可变记录状态。 */
        private String recordStatus;
    }

    /** 一条原标签币种保证金清分明细，不包含任何汇率。 */
    @Data
    public static class ReserveLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 保证金清分明细编号。 */
        private String reserveClearingDetailNo;
        /** 保证金来源原支付交易号。 */
        private String originalTransactionId;
        /** 来源保证金事实明细号。 */
        private String sourceReserveDetailNo;
        /** 所属清分修订号。 */
        private Integer clearingRevision;
        /** 当前修订内稳定行号。 */
        private Integer lineNo;
        /** HOLD、RETURN、RELEASE 或 ADJUSTMENT。 */
        private String reserveActionType;
        /** 保证金项目代码。 */
        private String itemCode;
        /** 保证金项目显示名称。 */
        private String itemName;
        /** 商户保证金责任方向。 */
        private String direction;
        /** 保证金原标签币种。 */
        private String reserveCurrency;
        /** 保证金币种 exponent。 */
        private Integer reserveCurrencyExponent;
        /** 原标签币种计算基数。 */
        private BigDecimal basisAmount;
        /** 保证金百分比数值。 */
        private BigDecimal reserveRate;
        /** 累计扣留金额。 */
        private BigDecimal retainedAmount;
        /** 累计退款返还金额。 */
        private BigDecimal returnedAmount;
        /** 累计到期释放金额。 */
        private BigDecimal releasedAmount;
        /** 当前保证金调整金额。 */
        private BigDecimal adjustmentAmount;
        /** 当前保证金责任余额。 */
        private BigDecimal remainingAmount;
        /** 预计释放业务日期。 */
        private LocalDate expectedReserveReleaseDate;
        /** 生成该明细的规则公式快照。 */
        private String formulaSnapshot;
        /** 不可变记录状态。 */
        private String recordStatus;
    }

    /** 清分摘要、交易明细和保证金明细详情响应。 */
    @Data
    public static class DetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 清分主摘要。 */
        private Summary summary;
        /** 交易费用和本金清分明细。 */
        private List<TransactionLine> transactionDetails = Collections.emptyList();
        /** 独立保证金清分明细。 */
        private List<ReserveLine> reserveDetails = Collections.emptyList();
    }

    /** 浏览器命令不含 operator，应用层从登录上下文补齐。 */
    @Data
    public static class ActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 交易时间，用于定位交易物理季度。 */
        private LocalDateTime transactionDateTime;
        /** 清分财务状态期望乐观锁版本。 */
        private Integer expectedVersion;
        /** 人工操作原因，必填且长度受限。 */
        private String reason;
    }

    /** 浏览器单笔重算命令，指定当前清分修订和目标不可变费用版本。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RecalculateRequest extends ActionRequest {
        private static final long serialVersionUID = 1L;
        /** 期望当前清分修订号，防止基于旧明细重算。 */
        private Integer expectedClearingRevision;
        /** 目标费用方案主键。 */
        private Long targetFeePlanId;
        /** 目标已发布费用版本主键。 */
        private Long targetFeePlanVersionId;
    }

    /** 清分重算可选的不可变费用版本描述；数据库主键仅作为提交载荷，不要求运营人员输入。 */
    @Data
    public static class RecalculationVersionOption implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 费用版本主键，仅作为提交载荷。 */
        private Long versionId;
        /** 面向运营展示的版本号。 */
        private Integer versionNo;
        /** 费用版本状态。 */
        private String versionStatus;
    }

    /** 指定商户费用方案的清分重算选项，不返回费用规则和金额配置。 */
    @Data
    public static class RecalculationOptionsResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 商户号。 */
        private String merchantId;
        /** 费用方案主键。 */
        private Long feePlanId;
        /** 费用方案编码。 */
        private String planCode;
        /** 费用方案名称。 */
        private String planName;
        /** 当前清分正在使用的费用版本主键。 */
        private Long currentVersionId;
        /** 可选择的已发布不可变版本。 */
        private List<RecalculationVersionOption> versions = Collections.emptyList();
    }

    /** 批量重算中的单笔身份和运营人员看到的 CAS 版本。 */
    @Data
    public static class RecalculateBatchItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 平台交易号。 */
        private String transactionId;
        /** 交易时间，用于定位物理季度。 */
        private LocalDateTime transactionDateTime;
        /** 清分财务状态期望版本。 */
        private Integer expectedVersion;
        /** 期望当前清分修订号。 */
        private Integer expectedClearingRevision;
    }

    /** 同一商户、同一费用方案的一组未结算清分重算请求。 */
    @Data
    public static class RecalculateBatchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 待重算交易，数量限制为 2 至 20 且引用不得重复。 */
        private List<RecalculateBatchItem> records = Collections.emptyList();
        /** 所有交易共同使用的费用方案主键。 */
        private Long targetFeePlanId;
        /** 所有交易共同使用的目标不可变费用版本主键。 */
        private Long targetFeePlanVersionId;
        /** 统一人工重算原因。 */
        private String reason;
    }

    /** 批量重算逐笔结果；批量请求不伪装成跨交易原子事务。 */
    @Data
    public static class RecalculateBatchItemResult implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 平台交易号。 */
        private String transactionId;
        /** 交易时间。 */
        private LocalDateTime transactionDateTime;
        /** 当前交易重算是否成功。 */
        private boolean success;
        /** COMPLETED、FAILED 或 STALE_OR_INELIGIBLE 等结果码。 */
        private String result;
        /** 脱敏结果摘要，成功时可空。 */
        private String message;
    }

    /** 批量重算汇总及逐笔成功、失败结果。 */
    @Data
    public static class RecalculateBatchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 请求重算笔数。 */
        private Integer requestedCount;
        /** 成功笔数。 */
        private Integer successCount;
        /** 失败或不再可重算笔数。 */
        private Integer failureCount;
        /** 逐笔独立结果。 */
        private List<RecalculateBatchItemResult> results = Collections.emptyList();
    }

    /** Admin 到 clearing 服务的人工动作命令，operator 只能由应用层注入。 */
    @Data
    public static class InternalActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 交易时间，用于定位物理季度。 */
        private LocalDateTime transactionDateTime;
        /** 清分财务状态期望版本。 */
        private Integer expectedVersion;
        /** 人工操作原因。 */
        private String reason;
        /** 可信 Admin 操作人名称。 */
        private String operator;
    }

    /** Admin 到 clearing 服务的重算命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalRecalculateRequest extends InternalActionRequest {
        private static final long serialVersionUID = 1L;
        /** 期望当前清分修订号。 */
        private Integer expectedClearingRevision;
        /** 目标费用方案主键。 */
        private Long targetFeePlanId;
        /** 目标已发布费用版本主键。 */
        private Long targetFeePlanVersionId;
    }

    /** 浏览器提交的保证金标签币种差额申请，不允许携带提交人。 */
    @Data
    public static class ReserveAdjustmentSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 客户端生成的幂等请求键。 */
        private String requestKey;
        /** 保证金责任状态业务编号。 */
        private String reserveStateId;
        /** 保证金来源原支付交易号。 */
        private String originalTransactionId;
        /** 原支付交易时间，用于物理季度定位。 */
        private LocalDateTime originalTransactionDateTime;
        /** 保证金责任状态期望版本。 */
        private Long expectedReserveStateVersion;
        /** DEBIT 增加责任，CREDIT 减少责任。 */
        private String direction;
        /** 原标签币种非负调整金额，不参与换汇。 */
        private BigDecimal adjustmentAmount;
        /** 调整后请求的释放日期，可空。 */
        private LocalDate requestedReleaseDate;
        /** 人工调整原因。 */
        private String reason;
    }

    /** Admin 到清分服务的调整申请，提交人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalReserveAdjustmentSubmitRequest extends ReserveAdjustmentSubmitRequest {
        private static final long serialVersionUID = 1L;
        /** 可信 Admin 提交人名称。 */
        private String submitOperator;
    }

    /** 浏览器提交的保证金调整复核命令，不允许携带复核人。 */
    @Data
    public static class ReserveAdjustmentReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调整申请期望版本。 */
        private Long expectedRequestVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** 复核意见。 */
        private String reviewComment;
    }

    /** Admin 到清分服务的复核命令，复核人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalReserveAdjustmentReviewRequest extends ReserveAdjustmentReviewRequest {
        private static final long serialVersionUID = 1L;
        /** 可信 Admin 复核人名称。 */
        private String reviewOperator;
    }

    /** 保证金调整申请或复核结果，不包含费用配置及余额信息。 */
    @Data
    public static class ReserveAdjustmentResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 保证金调整申请号。 */
        private String adjustmentNo;
        /** 调整申请当前状态。 */
        private String status;
        /** 调整清分事实对应的平台交易号。 */
        private String transactionId;
        /** 生成调整事实时采用的来源清分修订号。 */
        private Integer sourceRevision;
        /** 调整申请乐观锁版本。 */
        private Long version;
    }

    /** 浏览器提交的阶梯期间重放申请，不允许携带提交人。 */
    @Data
    public static class TierPeriodReplaySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 客户端生成的幂等请求键。 */
        private String requestKey;
        /** 商户号。 */
        private String merchantId;
        /** 费用方案主键。 */
        private Long feePlanId;
        /** 冻结费用版本主键。 */
        private Long feePlanVersionId;
        /** 触发阶梯重放的费用规则主键。 */
        private Long triggerFeeRuleId;
        /** 月度期间键，格式 yyyyMM。 */
        private String periodKey;
        /** 人工重放原因。 */
        private String reason;
    }

    /** Admin 到清分服务的重放申请，提交人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalTierPeriodReplaySubmitRequest extends TierPeriodReplaySubmitRequest {
        private static final long serialVersionUID = 1L;
        /** 可信 Admin 提交人名称。 */
        private String submitOperator;
    }

    /** 浏览器提交的阶梯期间重放复核命令，不允许携带复核人。 */
    @Data
    public static class TierPeriodReplayReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 重放申请期望版本。 */
        private Long expectedRequestVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** 复核意见。 */
        private String reviewComment;
    }

    /** Admin 到清分服务的重放复核命令，复核人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalTierPeriodReplayReviewRequest extends TierPeriodReplayReviewRequest {
        private static final long serialVersionUID = 1L;
        /** 可信 Admin 复核人名称。 */
        private String reviewOperator;
    }

    /** 阶梯期间重放控制结果。 */
    @Data
    public static class TierPeriodReplayResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 阶梯期间重放申请号。 */
        private String replayNo;
        /** 重放申请当前状态。 */
        private String status;
        /** 重放明细总数。 */
        private Integer itemCount;
        /** 已完成重放明细数。 */
        private Integer completedCount;
        /** 重放申请乐观锁版本。 */
        private Long version;
    }

    /** 清分人工命令结果。 */
    @Data
    public static class CommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 平台交易号。 */
        private String transactionId;
        /** 交易时间。 */
        private LocalDateTime transactionDateTime;
        /** 实际执行动作。 */
        private String action;
        /** 命令后的清分状态。 */
        private String clearingStatus;
        /** 命令后的清分修订号。 */
        private Integer clearingRevision;
        /** 命令后的乐观锁版本。 */
        private Integer version;
        /** 幂等命令结果摘要。 */
        private String result;
    }
}
