package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewOrderDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算预审单主表；冻结选择、清分、统一汇率和财务结果指纹，并以请求唯一键、Maker-Checker 和 version CAS 保护终态决策。
 * @status : create
 */
@Data
@TableName("settlement_review_order")
public class SettlementReviewOrderDO {
    /** 预审单数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** SMyyyyMMdd-NNNNNNNN 预审单号。 */
    private String reviewOrderNo;
    /** 创建请求幂等键，数据库必须唯一。 */
    private String createRequestKey;
    /** 候选 ID 与期望版本集合的 SHA-256 指纹。 */
    private String selectionFingerprint;
    /** REGULAR、RESERVE_RELEASE 或 ADJUSTMENT，REVERSAL 不走预审。 */
    private String reviewType;
    /** MANUAL_REVIEW 或系统自动预审模式。 */
    private String createMode;
    /** 候选统一所属平台商户号。 */
    private String merchantId;
    /** 提交时冻结的商户结算档案主键。 */
    private Long settlementProfileId;
    /** 提交时冻结的 NORMAL 结算资金账户主键。 */
    private Long settlementAccountId;
    /** 统一目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位，决定净额展示和舍入精度。 */
    private Integer targetCurrencyExponent;
    /** 结算日历业务日期。 */
    private LocalDate businessDate;
    /** 结算日历 IANA 时区。 */
    private String businessTimeZone;
    /** 纳入候选的窗口闭区间起点。 */
    private LocalDateTime cutoffBeginTime;
    /** 纳入候选的窗口开区间终点。 */
    private LocalDateTime cutoffEndTime;
    /** 预审独占锁定候选总数。 */
    private Integer candidateCount;
    /** 其中真实 CLEARING_REVISION 候选数；只为这些候选生成交易投影。 */
    private Integer projectableCandidateCount;
    /** 候选对应清分事实集合 SHA-256 指纹。 */
    private String sourceFingerprint;
    /** 统一锁定汇率矩阵 SHA-256 指纹。 */
    private String rateFingerprint;
    /** 逐项计算及汇总结果 SHA-256 指纹。 */
    private String resultFingerprint;
    /** 冻结净结果 CREDIT 或 DEBIT 方向。 */
    private String netDirection;
    /** 冻结非负净额，单位由 targetCurrencyExponent 决定。 */
    private BigDecimal netAmount;
    /** PENDING_APPROVAL、APPROVED、REJECTED、CANCELLED 或 EXPIRED。 */
    private String reviewStatus;
    /** 创建预审单的主体 ID，人工和系统任务均保留。 */
    private Long createdByAccountId;
    /** 创建主体展示名。 */
    private String createdByAccountName;
    /** Maker 管理账户 ID。 */
    private Long submittedByAccountId;
    /** Maker 管理账户展示名。 */
    private String submittedByAccountName;
    /** 提交时角色权限快照，不包含鉴权凭据。 */
    private String submittedRoleSnapshot;
    /** 提交客户端 IP 审计值。 */
    private String submitClientIp;
    /** 提交客户端 User-Agent 审计值。 */
    private String submitUserAgent;
    /** 预审提交原因，不允许为空。 */
    private String submitReason;
    /** Maker 实际提交时间，数据库精度为毫秒。 */
    private LocalDateTime submittedTime;
    /** Checker 管理账户 ID；待复核时为空。 */
    private Long decidedByAccountId;
    /** Checker 管理账户展示名；待复核时为空。 */
    private String decidedByAccountName;
    /** 决策时角色权限快照；待复核时为空。 */
    private String decidedRoleSnapshot;
    /** 决策客户端 IP 审计值；待复核时为空。 */
    private String decisionClientIp;
    /** 决策客户端 User-Agent 审计值；待复核时为空。 */
    private String decisionUserAgent;
    /** APPROVE、REJECT 或 CANCEL；待复核时为空。 */
    private String decisionAction;
    /** 决策请求幂等键，终态后数据库唯一。 */
    private String decisionRequestKey;
    /** Checker 决策意见；待复核时为空。 */
    private String reviewComment;
    /** Checker 实际决策时间；待复核时为空。 */
    private LocalDateTime decisionTime;
    /** 批准后创建的正式结算批次号；其他状态为空。 */
    private String settlementBatchNo;
    /** 预审状态终态 CAS 版本。 */
    private Long version;
    /** 预审单创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 预审单最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
