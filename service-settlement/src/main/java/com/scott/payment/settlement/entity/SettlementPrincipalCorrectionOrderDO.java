package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已入账历史标签本金差额调整主单；冻结原批次身份、合计差额和完整 Maker-Checker 审计。
 */
@Data
public class SettlementPrincipalCorrectionOrderDO {
    /** 数据库主键，新增前允许为空。 */
    private Long id;
    /** 调整主单稳定业务号。 */
    private String correctionNo;
    /** Maker 提交幂等键，数据库全局唯一。 */
    private String submitRequestKey;
    /** 被修复的原 POSTED REGULAR 结算批次号。 */
    private String originalBatchNo;
    /** 原批次所属平台商户号。 */
    private String merchantId;
    /** 原批次已经入账的商户结算资金账户。 */
    private Long settlementAccountId;
    /** 差额记账所用原批次目标 ISO 币种。 */
    private String targetCurrency;
    /** 目标币种 ISO exponent，决定唯一舍入边界。 */
    private Integer targetCurrencyExponent;
    /** 申请时冻结的原批次 CAS 版本。 */
    private Long originalBatchVersion;
    /** 原批次不可变净入账资金流水主键。 */
    private Long originalFundLedgerId;
    /** 调整单冻结的异常本金结果项数量。 */
    private Integer itemCount;
    /** 商户余额视角的 CREDIT 或 DEBIT 差额方向。 */
    private String correctionDirection;
    /** 目标币种非负差额合计。 */
    private BigDecimal correctionAmount;
    /** 原批次、流水、逐笔来源与计算结果的 SHA-256 指纹。 */
    private String sourceFingerprint;
    /** PENDING_REVIEW、EXECUTED 或 REJECTED。 */
    private String correctionStatus;
    /** Maker 管理账号 ID。 */
    private Long submittedByAccountId;
    /** Maker 展示名快照。 */
    private String submittedByAccountName;
    /** Maker 提交时角色集合快照。 */
    private String submittedRoleSnapshot;
    /** Maker 客户端 IP 审计值。 */
    private String submitClientIp;
    /** Maker 客户端 User-Agent 审计值。 */
    private String submitUserAgent;
    /** Maker 填写的业务调整原因。 */
    private String submitReason;
    /** Maker 实际提交时间。 */
    private LocalDateTime submittedTime;
    /** Checker 管理账号 ID，待复核时为空。 */
    private Long decidedByAccountId;
    /** Checker 展示名快照，待复核时为空。 */
    private String decidedByAccountName;
    /** Checker 决策时角色集合快照。 */
    private String decidedRoleSnapshot;
    /** Checker 客户端 IP 审计值。 */
    private String decisionClientIp;
    /** Checker 客户端 User-Agent 审计值。 */
    private String decisionUserAgent;
    /** APPROVE 或 REJECT。 */
    private String decisionAction;
    /** Checker 决策幂等键，决策后全局唯一。 */
    private String decisionRequestKey;
    /** Checker 复核意见。 */
    private String decisionComment;
    /** Checker 实际决策时间。 */
    private LocalDateTime decisionTime;
    /** 非零差额对应的唯一新增资金流水 ID；零差额为空。 */
    private Long correctionLedgerId;
    /** 已完成三层交易投影修正的逐笔数量。 */
    private Integer projectionCompletedCount;
    /** 调整单状态迁移 CAS 版本。 */
    private Long version;
    /** 调整单创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 调整单最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
