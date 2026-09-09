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

    /** 真实交易结算明细查询条件。 */
    @Data
    public static class TransactionItemQuery {
        /** 精确结算批次号，可空。 */
        private String settlementBatchNo;
        /** 精确平台交易号，可空。 */
        private String sourceTransactionId;
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
        /** 精确来源平台交易号，可空。 */
        private String sourceTransactionId;
        /** 保证金动作类型，可空且受服务端白名单限制。 */
        private String actionType;
        /** 保证金原标签币种，可空。 */
        private String currency;
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
