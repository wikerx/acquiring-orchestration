package com.scott.payment.clearing.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingManagementDTOs
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分内部查询与管理接口模型，不包含持卡人、卡号或费用配置正文。
 * @status : update
 */
public final class ClearingManagementDTOs {

    private ClearingManagementDTOs() {
    }

    /** 动作级清分汇总。 */
    @Data
    public static class ClearingRecordSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 财务状态表主键，仅用于游标。 */
        private Long id;
        /** 动作财务状态业务号。 */
        private String financeStateId;
        /** 动作交易号。 */
        private String transactionId;
        /** 动作操作号。 */
        private String operationId;
        /** 平台商户号。 */
        private String merchantId;
        /** 关联源交易号；无源动作时为空。 */
        private String sourceTransactionId;
        /** 平台统一交易类型。 */
        private String transactionType;
        /** 商户标签 ISO 币种。 */
        private String labelCurrency;
        /** 清分权威状态。 */
        private String clearingStatus;
        /** 当前有效清分修订。 */
        private Integer clearingRevision;
        /** 业务延时重试累计次数。 */
        private Integer clearingRetryCount;
        /** 下一次允许重试 UTC 时间；无需重试时为空。 */
        private LocalDateTime nextRetryTime;
        /** 最近稳定失败码；无失败时为空。 */
        private String lastFailureCode;
        /** 已限制长度的非敏感失败摘要；无失败时为空。 */
        private String lastFailureMessage;
        /** 动作冻结费用方案 ID。 */
        private Long feePlanId;
        /** 动作冻结不可变费用版本 ID。 */
        private Long feePlanVersionId;
        /** 动作冻结费用版本号。 */
        private Integer feePlanVersionNo;
        /** 清分本金标签金额，十进制主单位。 */
        private BigDecimal grossLabelAmount;
        /** 跨币种费用限额求值状态摘要。 */
        private String feeEvaluationStatus;
        /** 当前结算状态；清分查询只读。 */
        private String settlementStatus;
        /** 档案目标结算币种；不代表已换汇。 */
        private String settlementCurrency;
        /** 最早可结算业务日。 */
        private LocalDate settlementEligibleDate;
        /** 单币种可汇总平台费用；多币种时为空。 */
        private BigDecimal platformFeeAmount;
        /** 单币种可汇总费用返还；多币种时为空。 */
        private BigDecimal feeReversalAmount;
        /** 本动作保证金扣留金额，标签币种十进制主单位。 */
        private BigDecimal reserveAmount;
        /** 本动作保证金返还金额，标签币种十进制主单位。 */
        private BigDecimal reserveReversalAmount;
        /** 预计保证金释放业务日；无扣留时为空。 */
        private LocalDate expectedReserveReleaseDate;
        /** 动作季度分片时间。 */
        private LocalDateTime transactionDateTime;
        /** 动作 UTC 业务时间。 */
        private LocalDateTime transactionUtcTime;
        /** 动作业务 IANA 时区。 */
        private String transactionTimeZone;
        /** 财务状态 CAS 版本，人工命令必须原样回传。 */
        private Integer version;
    }

    /** 交易本金或费用原子明细。 */
    @Data
    public static class ClearingTransactionLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 清分明细业务号。 */
        private String clearingDetailNo;
        /** 所属清分修订。 */
        private Integer clearingRevision;
        /** 修订内稳定行号。 */
        private Integer lineNo;
        /** 原子项目类型：本金、费用或返费。 */
        private String itemType;
        /** 费用类别；本金行为空。 */
        private String feeCategory;
        /** 风险服务类型；非风险费时为空。 */
        private String riskServiceType;
        /** 原子项目稳定编码。 */
        private String itemCode;
        /** 形成事实时冻结的展示名称。 */
        private String itemName;
        /** 相对商户结算资金的方向。 */
        private String direction;
        /** 商户标签 ISO 币种。 */
        private String labelCurrency;
        /** 商户标签金额，十进制主单位。 */
        private BigDecimal labelAmount;
        /** 费用组件类型；本金行为空。 */
        private String componentType;
        /** 组件计费基础 ISO 币种。 */
        private String basisCurrency;
        /** 组件计费基础金额，十进制主单位。 */
        private BigDecimal basisAmount;
        /** 原子行金额，十进制主单位。 */
        private BigDecimal amount;
        /** 原子行 ISO 币种。 */
        private String currency;
        /** 原子行币种 ISO exponent。 */
        private Integer currencyExponent;
        /** 标签金额百分比费率。 */
        private BigDecimal percentageRate;
        /** 固定单笔费，币种固定为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用限制，币种固定为 USD。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用限制，币种固定为 USD。 */
        private BigDecimal maximumAmountUsd;
        /** 跨币种限额求值状态。 */
        private String limitEvaluationStatus;
        /** 实际命中的限制；未命中时为空。 */
        private String appliedLimit;
        /** 可审计公式快照，不包含敏感支付数据。 */
        private String formulaSnapshot;
        /** 修订记录状态。 */
        private String recordStatus;
    }

    /** 独立保证金清分明细。 */
    @Data
    public static class ClearingReserveLine implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 保证金明细业务号。 */
        private String reserveClearingDetailNo;
        /** 原支付交易号。 */
        private String originalTransactionId;
        /** 引用的源保证金明细号；HOLD 时为空。 */
        private String sourceReserveDetailNo;
        /** 所属清分修订。 */
        private Integer clearingRevision;
        /** 修订内稳定行号。 */
        private Integer lineNo;
        /** 保证金动作类型。 */
        private String reserveActionType;
        /** 稳定项目编码。 */
        private String itemCode;
        /** 形成事实时冻结的展示名称。 */
        private String itemName;
        /** 相对商户资金的方向。 */
        private String direction;
        /** 原支付标签 ISO 币种。 */
        private String reserveCurrency;
        /** 保证金币种 ISO exponent。 */
        private Integer reserveCurrencyExponent;
        /** 保证金计算基础金额，标签币种十进制主单位。 */
        private BigDecimal basisAmount;
        /** 计提保证金比例。 */
        private BigDecimal reserveRate;
        /** 本行扣留金额。 */
        private BigDecimal retainedAmount;
        /** 本行退款返还金额。 */
        private BigDecimal returnedAmount;
        /** 本行到期释放金额。 */
        private BigDecimal releasedAmount;
        /** 本行人工调整金额，方向由 direction 表示。 */
        private BigDecimal adjustmentAmount;
        /** 本行提交后的剩余保证金。 */
        private BigDecimal remainingAmount;
        /** 预计释放业务日；无需释放时为空。 */
        private LocalDate expectedReserveReleaseDate;
        /** 可审计公式快照，不包含敏感支付数据。 */
        private String formulaSnapshot;
        /** 修订记录状态。 */
        private String recordStatus;
    }

    /** 单笔动作清分聚合详情。 */
    @Data
    public static class ClearingRecordDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作级清分权威摘要。 */
        private ClearingRecordSummary summary;
        /** 当前修订的本金、费用和返费原子明细，默认空列表。 */
        private List<ClearingTransactionLine> transactionDetails = Collections.emptyList();
        /** 当前修订的保证金原子明细，默认空列表。 */
        private List<ClearingReserveLine> reserveDetails = Collections.emptyList();
    }

    /** 单季度清分记录游标查询。 */
    @Data
    public static class ClearingRecordSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 平台商户号筛选；为空时不限制。 */
        private String merchantId;
        /** 动作交易号筛选；为空时不限制。 */
        private String transactionId;
        /** 清分权威状态筛选；为空时不限制。 */
        private String clearingStatus;
        /** 单季度半开时间窗口起点。 */
        private LocalDateTime beginTime;
        /** 单季度半开时间窗口终点。 */
        private LocalDateTime endTime;
        /** 上一页最后动作分片时间；首页为空。 */
        private LocalDateTime cursorTransactionDateTime;
        /** 上一页最后财务状态主键；必须与时间游标同时为空或同时有值。 */
        private Long cursorId;
        /** 单页数量，由服务端限制最大值。 */
        private Integer limit;
    }

    /** 清分记录游标查询响应。 */
    @Data
    public static class ClearingRecordSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 本页动作级清分摘要，默认空列表。 */
        private List<ClearingRecordSummary> records = Collections.emptyList();
        /** 是否仍存在下一页。 */
        private boolean hasMore;
        /** 下一页动作分片时间游标；无下一页时为空。 */
        private LocalDateTime nextCursorTransactionDateTime;
        /** 下一页财务状态主键游标；无下一页时为空。 */
        private Long nextCursorId;
    }

    /** 人工重试命令；分片时间和预期版本共同防止误操作旧状态。 */
    @Data
    public static class ClearingRetryRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作季度分片时间，不允许为空。 */
        private LocalDateTime transactionDateTime;
        /** 财务状态预期 CAS 版本。 */
        private Integer expectedVersion;
        /** 运营重试原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 注入的可信操作人，不信任浏览器原值。 */
        private String operator;
    }

    /** 人工复核升级命令，不允许从浏览器直接指定任意目标状态。 */
    @Data
    public static class ClearingReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作季度分片时间，不允许为空。 */
        private LocalDateTime transactionDateTime;
        /** 财务状态预期 CAS 版本。 */
        private Integer expectedVersion;
        /** 运营复核原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 注入的可信复核人，不信任浏览器原值。 */
        private String operator;
    }

    /** 未结算清分重算命令，目标必须是一个明确的不可变费用版本。 */
    @Data
    public static class ClearingRecalculateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作季度分片时间，不允许为空。 */
        private LocalDateTime transactionDateTime;
        /** 财务状态预期 CAS 版本。 */
        private Integer expectedVersion;
        /** 当前预期清分修订。 */
        private Integer expectedClearingRevision;
        /** 目标费用方案 ID。 */
        private Long targetFeePlanId;
        /** 目标不可变费用版本 ID。 */
        private Long targetFeePlanVersionId;
        /** 运营重算原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 注入的可信操作人。 */
        private String operator;
    }

    /** 清分人工命令结果，只返回状态和并发控制字段。 */
    @Data
    public static class ClearingCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 动作交易号。 */
        private String transactionId;
        /** 动作季度分片时间。 */
        private LocalDateTime transactionDateTime;
        /** 已执行的固定命令类型。 */
        private String action;
        /** 命令提交后的清分权威状态。 */
        private String clearingStatus;
        /** 命令提交后的有效清分修订。 */
        private Integer clearingRevision;
        /** 命令提交后的财务状态 CAS 版本。 */
        private Integer version;
        /** 稳定命令结果码。 */
        private String result;
    }

    /** 保证金差额调整申请；金额币种由原保证金状态冻结，调用方不能另传币种。 */
    @Data
    public static class ReserveAdjustmentSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调用方幂等键。 */
        private String requestKey;
        /** 原保证金状态业务号。 */
        private String reserveStateId;
        /** 原支付交易号。 */
        private String originalTransactionId;
        /** 原支付季度分片时间。 */
        private LocalDateTime originalTransactionDateTime;
        /** 原保证金状态预期 CAS 版本。 */
        private Long expectedReserveStateVersion;
        /** DEBIT 增加负债或 CREDIT 减少负债。 */
        private String direction;
        /** 原保证金标签币种正数金额，调用方不得另传币种。 */
        private BigDecimal adjustmentAmount;
        /** DEBIT 调整计划释放日；CREDIT 时为空。 */
        private LocalDate requestedReleaseDate;
        /** 运营提交原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 注入的可信提交人。 */
        private String submitOperator;
    }

    /** 保证金调整双人复核命令；期望版本防止重复或过期决定覆盖终态。 */
    @Data
    public static class ReserveAdjustmentReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调整申请预期 CAS 版本。 */
        private Long expectedRequestVersion;
        /** 固定复核决定 APPROVE 或 REJECT。 */
        private String decision;
        /** 复核意见，不得包含敏感支付数据。 */
        private String reviewComment;
        /** service-admin 注入的可信复核人，必须与提交人不同。 */
        private String reviewOperator;
    }

    /** 保证金调整申请与执行结果。 */
    @Data
    public static class ReserveAdjustmentResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调整申请业务号。 */
        private String adjustmentNo;
        /** 已持久化申请状态。 */
        private String status;
        /** 批准后生成的独立调整动作号；未资金化时为空。 */
        private String transactionId;
        /** 保证金状态资金化修订；未资金化时为零。 */
        private Integer sourceRevision;
        /** 当前申请 CAS 版本。 */
        private Long version;
    }

    /** 阶梯期间重放申请；范围固定为商户、不可变费用版本和 yyyyMM 月份。 */
    @Data
    public static class TierPeriodReplaySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调用方幂等键。 */
        private String requestKey;
        /** 目标平台商户号。 */
        private String merchantId;
        /** 目标费用方案 ID。 */
        private Long feePlanId;
        /** 目标不可变费用版本 ID。 */
        private Long feePlanVersionId;
        /** 触发重放的阶梯规则 ID。 */
        private Long triggerFeeRuleId;
        /** 月度期间键，格式 yyyy-MM。 */
        private String periodKey;
        /** 运营提交原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 注入的可信提交人。 */
        private String submitOperator;
    }

    /** 阶梯期间重放双人复核命令，版本 CAS 防止过期决定覆盖状态。 */
    @Data
    public static class TierPeriodReplayReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 重放申请预期 CAS 版本。 */
        private Long expectedRequestVersion;
        /** 固定复核决定 APPROVE 或 REJECT。 */
        private String decision;
        /** 复核意见，不得包含敏感支付数据。 */
        private String reviewComment;
        /** service-admin 注入的可信复核人，必须与提交人不同。 */
        private String reviewOperator;
    }

    /** 阶梯期间重放申请、准备或推进结果，不返回费用配置正文。 */
    @Data
    public static class TierPeriodReplayResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 阶梯期间重放业务号。 */
        private String replayNo;
        /** 已持久化重放状态。 */
        private String status;
        /** 冻结动作项总数。 */
        private Integer itemCount;
        /** 已按序完成动作项数量。 */
        private Integer completedCount;
        /** 当前重放控制行 CAS 版本。 */
        private Long version;
    }
}
