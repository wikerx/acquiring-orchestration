package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调整单逐笔冻结事实和可靠投影任务；不替代或改写原清分、结算结果及资金流水。
 */
@Data
public class SettlementPrincipalCorrectionItemDO {
    /** 数据库主键，新增前允许为空。 */
    private Long id;
    /** 逐笔调整稳定业务号。 */
    private String correctionItemNo;
    /** 所属调整主单号。 */
    private String correctionNo;
    /** 原不可变 PRINCIPAL 结算结果项主键。 */
    private Long originalResultItemId;
    /** 原已完成结算投影任务主键。 */
    private Long originalProjectionTaskId;
    /** 原已完成结算投影任务号。 */
    private String originalProjectionTaskNo;
    /** 原批次真实交易候选主键。 */
    private Long candidateId;
    /** 原 ACTIVE 本金清分明细号。 */
    private String clearingDetailNo;
    /** 待修正投影的平台动作交易号。 */
    private String transactionId;
    /** 动作交易季度精确分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 原已入账清分修订号。 */
    private Integer clearingRevision;
    /** 平台交易生命周期号。 */
    private String operationId;
    /** 交易所属平台商户号。 */
    private String merchantId;
    /** 原本金商户视角 CREDIT 或 DEBIT 方向。 */
    private String direction;
    /** 原错误本金额，保留审计且不覆盖原结果。 */
    private BigDecimal originalSourceAmount;
    /** 原错误本金 ISO 币种。 */
    private String originalSourceCurrency;
    /** 原错误本金币种 exponent。 */
    private Integer originalSourceCurrencyExponent;
    /** 商户请求标签本金金额。 */
    private BigDecimal labelAmount;
    /** 商户请求标签 ISO 币种。 */
    private String labelCurrency;
    /** 标签币种 exponent。 */
    private Integer labelCurrencyExponent;
    /** 原历史结果实际引用的批次锁定汇率主键。 */
    private Long originalLockedRateId;
    /** 原历史结果实际引用的直接汇率。 */
    private BigDecimal originalLockedDirectRate;
    /** 标签本金重算使用的批次锁定汇率主键。 */
    private Long labelLockedRateId;
    /** 标签本金重算使用的直接汇率，label source 乘该值等于 target。 */
    private BigDecimal labelLockedDirectRate;
    /** 原批次目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 目标结算币种 exponent。 */
    private Integer targetCurrencyExponent;
    /** 原结算结果冻结的舍入模式。 */
    private String roundingMode;
    /** 原本金结果的目标币种金额。 */
    private BigDecimal originalTargetAmount;
    /** 标签本金按原锁定汇率仅舍入一次后的目标金额。 */
    private BigDecimal expectedTargetAmount;
    /** 商户余额视角逐笔有符号差额。 */
    private BigDecimal signedDeltaAmount;
    /** 原动作完整结算投影金额。 */
    private BigDecimal originalProjectionAmount;
    /** 原投影金额加本金差额后的期望金额。 */
    private BigDecimal correctedProjectionAmount;
    /** 原结算业务日期。 */
    private LocalDate settlementDate;
    /** 冻结、待处理、处理中、完成、失败或取消状态。 */
    private String projectionStatus;
    /** 投影修正累计失败次数。 */
    private Integer retryCount;
    /** 失败退避后的下次允许处理时间。 */
    private LocalDateTime nextRetryTime;
    /** 最近一次稳定失败码，不保存异常正文。 */
    private String lastFailureCode;
    /** 三层交易投影全部修正完成时间。 */
    private LocalDateTime completedTime;
    /** 投影任务状态迁移 CAS 版本。 */
    private Long version;
    /** 冻结事实创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 投影任务最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
