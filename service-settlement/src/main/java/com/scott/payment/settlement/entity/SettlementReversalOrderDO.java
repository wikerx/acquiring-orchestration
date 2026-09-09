package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalOrderDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算冲正申请及原批次资金快照；创建和决策分别以请求键唯一约束幂等，终态通过 version CAS 且强制 Maker-Checker 分离。
 * @status : create
 */
@Data
@TableName("settlement_reversal_order")
public class SettlementReversalOrderDO {
    /** 冲正单数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** RByyyyMMdd-NNNNNNNN 冲正申请单号。 */
    private String reversalOrderNo;
    /** 创建请求幂等键，数据库必须唯一。 */
    private String createRequestKey;
    /** 被冲正的已入账正式结算批次号。 */
    private String originalBatchNo;
    /** 批准后生成的独立反向批次号；待复核或拒绝时为空。 */
    private String reversalBatchNo;
    /** 原批次所属平台商户号。 */
    private String merchantId;
    /** 原批次入账资金账户主键。 */
    private Long settlementAccountId;
    /** 原批次目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** 提交时冻结的原批次版本，审批复核必须一致。 */
    private Long originalBatchVersion;
    /** 提交时冻结的原净入账结果项主键。 */
    private Long originalNetResultItemId;
    /** 提交时冻结的原资金流水主键。 */
    private Long originalFundLedgerId;
    /** 原净结果 CREDIT 或 DEBIT 方向。 */
    private String netDirection;
    /** 原净结果非负金额，单位由 targetCurrencyExponent 决定。 */
    private BigDecimal netAmount;
    /** 原批次、净结果、流水、投影和保证金动作的 SHA-256 指纹。 */
    private String sourceFingerprint;
    /** PENDING_APPROVAL、APPROVED 或 REJECTED。 */
    private String reversalStatus;
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
    /** 冲正申请原因，不允许为空。 */
    private String submitReason;
    /** Maker 实际操作时间，数据库精度为毫秒。 */
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
    /** APPROVE 或 REJECT；待复核时为空。 */
    private String decisionAction;
    /** 决策请求幂等键，终态后数据库唯一。 */
    private String decisionRequestKey;
    /** Checker 决策意见；待复核时为空。 */
    private String decisionComment;
    /** Checker 实际操作时间；待复核时为空。 */
    private LocalDateTime decisionTime;
    /** 冲正单终态 CAS 版本。 */
    private Long version;
    /** 冲正单创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 冲正单最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
