package com.scott.payment.merchant.dto.settlement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementDTOs
 * @date : 2026-09-01 22:40
 * @email : scott_x@163.com
 * @description : 商户结算账单、真实交易明细和保证金动作的只读接口模型；不包含内部审批、操作人或运维字段。
 * @status : update
 */
public final class MerchantSettlementDTOs {

    private MerchantSettlementDTOs() {
    }

    /** 结算批次分页查询条件；业务日期默认最近 30 天且最大跨度 92 天。 */
    @Data
    public static class BatchQuery {
        /** 精确结算批次号，可空。 */
        private String settlementBatchNo;
        /** 批次类型，可空，仅允许服务端白名单值。 */
        private String batchType;
        /** 批次状态，可空，商户侧仅可查询 POSTED 或 REVERSED。 */
        private String batchStatus;
        /** 业务日期起点，包含，可空。 */
        private LocalDate beginBusinessDate;
        /** 业务日期终点，包含，可空。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始，可空。 */
        private Integer pageNo;
        /** 页大小，可空且受交易逻辑数据源查询预算限制。 */
        private Integer pageSize;
    }

    /** 商户结算批次列表摘要。 */
    @Data
    public static class BatchSummary {
        /** 结算批次号。 */
        private String settlementBatchNo;
        /** 当前认证商户号。 */
        private String merchantId;
        /** 当前认证商户名称。 */
        private String merchantName;
        /** 结算资金账户主键。 */
        private Long settlementAccountId;
        /** 结算资金账户号。 */
        private String settlementAccountNo;
        /** 商户业务时区下的结算业务日期。 */
        private LocalDate businessDate;
        /** 生成业务日期时使用的 IANA 时区。 */
        private String businessTimeZone;
        /** 批次唯一目标结算币种，ISO 4217 三位代码。 */
        private String targetCurrency;
        /** 目标币种 exponent，用于页面金额展示。 */
        private Integer targetCurrencyExponent;
        /** REGULAR、RESERVE_RELEASE、REVERSAL 或 ADJUSTMENT。 */
        private String batchType;
        /** 商户可见终态：POSTED 或 REVERSED。 */
        private String batchStatus;
        /** 批次结果中去重后的真实来源交易数。 */
        private Long transactionCount;
        /** 批次认领的结算项目数，包含交易清分修订或保证金动作。 */
        private Integer candidateCount;
        /** 商户视角净入账方向：CREDIT 为增加，DEBIT 为扣减。 */
        private String netDirection;
        /** 目标结算币种净入账金额，按 targetCurrencyExponent 展示。 */
        private BigDecimal netAmount;
        /** 批次成功入账时间。 */
        private LocalDateTime postedTime;
        /** 批次创建时间。 */
        private LocalDateTime createTime;
    }

    /** 批次内一条不可变锁定汇率。 */
    @Data
    public static class RateLine {
        /** 汇率源币种。 */
        private String sourceCurrency;
        /** 汇率目标币种，与批次目标币种一致。 */
        private String targetCurrency;
        /** 一单位源币种对应目标币种的直接汇率。 */
        private BigDecimal directRate;
        /** 原始汇率生效时间。 */
        private LocalDateTime effectiveTime;
        /** 汇率锁定到结算批次的时间。 */
        private LocalDateTime lockedTime;
        /** 面向商户展示的统一汇率来源，不暴露内部供应商细节。 */
        private String displaySource;
    }

    /** 按支付维度、结果类型、费用类别、方向和币种聚合的批次结果。 */
    @Data
    public static class SummaryLine {
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
        /** 商户视角金额方向。 */
        private String direction;
        /** 清分来源币种。 */
        private String sourceCurrency;
        /** 来源币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 批次目标币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 当前聚合组去重后的真实交易数。 */
        private Long transactionCount;
        /** 当前聚合组原币种金额合计。 */
        private BigDecimal sourceAmount;
        /** 当前聚合组目标结算币种金额合计。 */
        private BigDecimal targetAmount;
    }

    /** 商户结算批次详情。 */
    @Data
    public static class BatchDetail {
        /** 批次摘要。 */
        private BatchSummary batch;
        /** 批次不可变锁定汇率列表。 */
        private List<RateLine> rates = new ArrayList<>();
        /** 批次维度结果汇总列表。 */
        private List<SummaryLine> summaries = new ArrayList<>();
    }

    /** 当前商户交易动作上的对账、结算和入账状态快照。 */
    @Data
    public static class ReconciliationRecord {
        /** 平台真实交易号。 */
        private String transactionId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 当前交易动作类型。 */
        private String transactionType;
        /** 当前对账状态。 */
        private String reconciliationStatus;
        /** 当前结算状态。 */
        private String settlementStatus;
        /** 当前入账状态。 */
        private String accountingStatus;
        /** 交易分片路由时间。 */
        private LocalDateTime transactionDateTime;
        /** 当前动作受理时间。 */
        private LocalDateTime operationTime;
    }

    /** 商户交易详情中的清分摘要；不暴露内部费用方案、失败堆栈或运维控制字段。 */
    @Data
    public static class ClearingSummary {
        /** 平台真实交易号。 */
        private String transactionId;
        /** 当前交易动作类型。 */
        private String transactionType;
        /** 交易标签币种。 */
        private String labelCurrency;
        /** 交易标签金额。 */
        private BigDecimal labelAmount;
        /** 当前清分状态。 */
        private String clearingStatus;
        /** 参与本金结算的标签币种毛额。 */
        private BigDecimal grossLabelAmount;
        /** 当前清分修订的平台费用合计。 */
        private BigDecimal platformFeeAmount;
        /** 当前清分修订的保证金扣留合计。 */
        private BigDecimal reserveAmount;
        /** 当前结算状态。 */
        private String settlementStatus;
        /** 最早可结算业务日期。 */
        private LocalDate settlementEligibleDate;
        /** 交易分片路由时间。 */
        private LocalDateTime transactionDateTime;
    }

    /** 商户可见的本金、费用或返费清分原子行。 */
    @Data
    public static class ClearingTransactionLine {
        /** 清分明细业务号。 */
        private String clearingDetailNo;
        /** 当前修订内稳定行号。 */
        private Integer lineNo;
        /** 本金、费用或返费项目类型。 */
        private String itemType;
        /** 费用类别，非费用项目可空。 */
        private String feeCategory;
        /** 项目代码。 */
        private String itemCode;
        /** 项目显示名称。 */
        private String itemName;
        /** 商户资金方向。 */
        private String direction;
        /** 计算基数币种。 */
        private String basisCurrency;
        /** 计算基数金额。 */
        private BigDecimal basisAmount;
        /** 清分金额。 */
        private BigDecimal amount;
        /** 清分金额币种。 */
        private String currency;
        /** 清分金额币种 exponent。 */
        private Integer currencyExponent;
        /** 不可变记录状态。 */
        private String recordStatus;
    }

    /** 商户可见的原标签币种保证金清分原子行。 */
    @Data
    public static class ClearingReserveLine {
        /** 保证金清分明细业务号。 */
        private String reserveClearingDetailNo;
        /** 当前修订内稳定行号。 */
        private Integer lineNo;
        /** HOLD、RETURN、RELEASE 或 ADJUSTMENT。 */
        private String reserveActionType;
        /** 项目代码。 */
        private String itemCode;
        /** 项目显示名称。 */
        private String itemName;
        /** 商户保证金责任方向。 */
        private String direction;
        /** 保证金原标签币种。 */
        private String reserveCurrency;
        /** 保证金币种 exponent。 */
        private Integer reserveCurrencyExponent;
        /** 累计扣留金额。 */
        private BigDecimal retainedAmount;
        /** 累计退款返还金额。 */
        private BigDecimal returnedAmount;
        /** 累计到期释放金额。 */
        private BigDecimal releasedAmount;
        /** 当前动作调整金额。 */
        private BigDecimal adjustmentAmount;
        /** 当前保证金责任余额。 */
        private BigDecimal remainingAmount;
        /** 预计释放业务日期。 */
        private LocalDate expectedReserveReleaseDate;
        /** 不可变记录状态。 */
        private String recordStatus;
    }

    /** 商户交易详情中的清分摘要和原子行。 */
    @Data
    public static class ClearingDetail {
        /** 清分摘要。 */
        private ClearingSummary summary;
        /** 本金、费用和返费清分行。 */
        private List<ClearingTransactionLine> transactionDetails = new ArrayList<>();
        /** 保证金清分行。 */
        private List<ClearingReserveLine> reserveDetails = new ArrayList<>();
    }

    /** 真实交易结算明细查询条件。 */
    @Data
    public static class TransactionItemQuery {
        /** 精确结算批次号，可空。 */
        private String settlementBatchNo;
        /** 精确平台交易号，可空。 */
        private String sourceTransactionId;
        /** 精确商户订单号，可空。 */
        private String merchantOrderNo;
        /** 原交易时间起点，包含，可空。 */
        private LocalDateTime beginTransactionTime;
        /** 原交易时间终点，包含，可空。 */
        private LocalDateTime endTransactionTime;
        /** 支付类型，可空。 */
        private String paymentType;
        /** 支付方式，可空。 */
        private String paymentMethod;
        /** 交易类型，可空。 */
        private String transactionType;
        /** 费用类别，可空。 */
        private String feeCategory;
        /** 批次业务日期起点，包含，可空。 */
        private LocalDate beginBusinessDate;
        /** 批次业务日期终点，包含，可空。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始，可空。 */
        private Integer pageNo;
        /** 页大小，可空且受查询预算限制。 */
        private Integer pageSize;
    }

    /** 商户侧一笔真实交易在正式结算批次中的汇总行。 */
    @Data
    public static class TransactionSettlement {
        /** 所属正式结算批次号。 */
        private String settlementBatchNo;
        /** 批次业务日期。 */
        private LocalDate businessDate;
        /** 批次状态。 */
        private String batchStatus;
        /** 来源候选主键。 */
        private Long candidateId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 平台真实交易号，即页面“系统订单号”。 */
        private String sourceTransactionId;
        /** 原交易时间。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 支付类型。 */
        private String paymentType;
        /** 支付方式。 */
        private String paymentMethod;
        /** 交易类型。 */
        private String transactionType;
        /** 原交易本金金额。 */
        private BigDecimal sourceAmount;
        /** 原交易币种。 */
        private String sourceCurrency;
        /** 原交易币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 当前交易包含的结算财务组件数。 */
        private Long componentCount;
        /** 财务组件相抵后的商户资金方向。 */
        private String netDirection;
        /** 财务组件相抵后的目标币种非负金额。 */
        private BigDecimal netTargetAmount;
        /** 批次目标结算币种。 */
        private String targetCurrency;
        /** 目标结算币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 批次成功入账时间。 */
        private LocalDateTime postedTime;
        /** 当前交易最后一条结果组件创建时间。 */
        private LocalDateTime createTime;
    }

    /** 真实交易对应的不可变结算财务行；金额、币种和汇率按数据库快照原样返回。 */
    @Data
    public static class TransactionItem {
        /** 结算结果明细号。 */
        private String settlementResultItemNo;
        /** 所属结算批次号。 */
        private String settlementBatchNo;
        /** 批次业务日期。 */
        private LocalDate businessDate;
        /** 真实来源平台交易号。 */
        private String sourceTransactionId;
        /** 来源交易时间，用于定位交易物理季度。 */
        private LocalDateTime sourceTransactionDateTime;
        /** 清分明细稳定编号。 */
        private String sourceDetailNo;
        /** 结算结果项目类型。 */
        private String resultItemType;
        /** 支付类型。 */
        private String paymentType;
        /** 支付方式。 */
        private String paymentMethod;
        /** 交易类型。 */
        private String transactionType;
        /** 费用类别，非费用项目可空。 */
        private String feeCategory;
        /** 商户视角方向。 */
        private String direction;
        /** 清分保存的原币种金额。 */
        private BigDecimal sourceAmount;
        /** 清分保存的原币种。 */
        private String sourceCurrency;
        /** 原币种 exponent。 */
        private Integer sourceCurrencyExponent;
        /** 一单位原币种对应目标币种的批次直接汇率。 */
        private BigDecimal directRate;
        /** 使用批次汇率折算后的目标币种金额。 */
        private BigDecimal targetAmount;
        /** 批次目标结算币种。 */
        private String targetCurrency;
        /** 目标币种 exponent。 */
        private Integer targetCurrencyExponent;
        /** 费用组命中的最低费或最高费边界，未命中为 NONE。 */
        private String appliedLimit;
        /** 结算结果明细创建时间。 */
        private LocalDateTime createTime;
    }

    /** 保证金动作明细查询条件。 */
    @Data
    public static class ReserveItemQuery {
        /** 精确结算批次号，可空。 */
        private String settlementBatchNo;
        /** 精确保证金责任编号，可空。 */
        private String reserveNo;
        /** 精确不可变保证金动作编号，可空。 */
        private String reserveActionNo;
        /** 精确来源平台交易号，可空。 */
        private String sourceTransactionId;
        /** 精确商户订单号，可空。 */
        private String merchantOrderNo;
        /** 保证金责任状态，可空。 */
        private String reserveStatus;
        /** 保证金动作类型，可空且受服务端白名单限制。 */
        private String actionType;
        /** 保证金原标签币种，可空。 */
        private String currency;
        /** 原交易时间起点，包含，可空。 */
        private LocalDateTime beginTransactionTime;
        /** 原交易时间终点，不包含，可空。 */
        private LocalDateTime endTransactionTime;
        /** 预计释放日期起点，包含，可空。 */
        private LocalDate beginExpectedReleaseDate;
        /** 预计释放日期终点，包含，可空。 */
        private LocalDate endExpectedReleaseDate;
        /** 批次业务日期起点，包含，可空。 */
        private LocalDate beginBusinessDate;
        /** 批次业务日期终点，包含，可空。 */
        private LocalDate endBusinessDate;
        /** 页码，从 1 开始，可空。 */
        private Integer pageNo;
        /** 页大小，可空且受查询预算限制。 */
        private Integer pageSize;
    }

    /** 保证金不可变动作及动作后的当前责任快照；保证金币种始终为原标签币种。 */
    @Data
    public static class ReserveItem {
        /** 不可变保证金动作编号。 */
        private String reserveActionNo;
        /** 保证金责任编号。 */
        private String reserveNo;
        /** 动作资金化所属结算批次号。 */
        private String settlementBatchNo;
        /** 批次业务日期。 */
        private LocalDate businessDate;
        /** 保证金来源真实平台交易号。 */
        private String sourceTransactionId;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 来源交易时间，用于定位交易物理季度。 */
        private LocalDateTime sourceTransactionDateTime;
        /** HOLD、RETURN、RELEASE、ADJUSTMENT 或相应冲正动作。 */
        private String actionType;
        /** 商户保证金责任方向。 */
        private String direction;
        /** 保证金原标签币种，不参与结算换汇。 */
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
        /** 累计增加保证金责任的借方调整金额。 */
        private BigDecimal debitAdjustmentAmount;
        /** 累计减少保证金责任的贷方调整金额。 */
        private BigDecimal creditAdjustmentAmount;
        /** 累计已冲正金额。 */
        private BigDecimal reversedAmount;
        /** 当前责任余额：扣留 + 借方调整 - 返还 - 释放 - 贷方调整 - 冲正。 */
        private BigDecimal remainingAmount;
        /** 保证金责任当前状态。 */
        private String reserveStatus;
        /** 预计可释放业务日期。 */
        private LocalDate expectedReleaseDate;
        /** 当前不可变动作发生时间。 */
        private LocalDateTime actionTime;
    }
}
