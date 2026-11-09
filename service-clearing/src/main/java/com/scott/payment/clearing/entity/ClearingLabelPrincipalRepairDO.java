package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未结算历史标签本金修复审批事实，冻结原修订、候选版本、本金快照和双人复核结果。
 */
@Data
@TableName("clearing_label_principal_repair")
public class ClearingLabelPrincipalRepairDO {
    /**
     * 数据库自增主键，不作为业务幂等身份。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 确定性修复单号，全局唯一。
     */
    private String repairNo;
    /**
     * 管理端生成的请求幂等键，全局唯一。
     */
    private String requestKey;
    /**
     * 待修复动作交易号。
     */
    private String transactionId;
    /**
     * 动作季度精确路由时间。
     */
    private LocalDateTime transactionDateTime;
    /**
     * 动作财务状态业务号。
     */
    private String financeStateId;
    /**
     * 平台商户号，仅用于归属审计。
     */
    private String merchantId;
    /**
     * 提交时冻结的财务状态 CAS 版本。
     */
    private Integer expectedFinanceStateVersion;
    /**
     * 提交时冻结的原清分修订。
     */
    private Integer expectedClearingRevision;
    /**
     * 提交时冻结的未认领结算候选主键。
     */
    private Long candidateId;
    /**
     * 提交时冻结的结算候选 CAS 版本。
     */
    private Long expectedCandidateVersion;
    /**
     * 原 ACTIVE 本金明细业务号。
     */
    private String principalDetailNo;
    /**
     * 修复前本金金额，十进制主单位。
     */
    private BigDecimal principalAmountBefore;
    /**
     * 修复前本金 ISO 4217 币种。
     */
    private String principalCurrencyBefore;
    /**
     * 修复前本金币种 exponent。
     */
    private Integer principalCurrencyExponentBefore;
    /**
     * 权威商户标签本金金额，十进制主单位。
     */
    private BigDecimal labelAmount;
    /**
     * 权威商户标签 ISO 4217 币种。
     */
    private String labelCurrency;
    /**
     * 权威标签币种 exponent。
     */
    private Integer labelCurrencyExponent;
    /**
     * 提交时全部 ACTIVE 清分明细的 SHA-256 快照。
     */
    private String sourceSnapshotHash;
    /**
     * 提交时当前修订 ACTIVE 保证金事实数量，只允许 0 或 1。
     */
    private Integer reserveFactCount;
    /**
     * 提交时当前修订 ACTIVE 保证金事实的 SHA-256 快照。
     */
    private String reserveSnapshotHash;
    /**
     * 提交时保证金状态数量；无保证金时为 0，有 HOLD 时必须为 1。
     */
    private Integer reserveStateCount;
    /**
     * 提交时完整保证金状态的 SHA-256 快照。
     */
    private String reserveStateSnapshotHash;
    /**
     * 提交时旧候选依赖关系数量，只允许 0 或 1。
     */
    private Integer dependencyCount;
    /**
     * 提交时依赖关系及依赖候选状态的 SHA-256 快照。
     */
    private String dependencySnapshotHash;
    /**
     * 运营修复原因，不得包含敏感支付数据。
     */
    private String reason;
    /**
     * service-admin 注入的可信 Maker 身份。
     */
    private String submitOperator;
    /**
     * service-admin 注入的可信 Checker 身份。
     */
    private String reviewOperator;
    /**
     * Checker 复核意见。
     */
    private String reviewComment;
    /**
     * 申请状态：PENDING_REVIEW、EXECUTED 或 REJECTED。
     */
    private String repairStatus;
    /**
     * 批准后生成的新清分修订；未执行时为空。
     */
    private Integer newClearingRevision;
    /**
     * 批准后生成的新结算候选号；未执行时为空。
     */
    private String newCandidateNo;
    /**
     * 批准后生成的新结算候选主键，用于依赖迁移审计。
     */
    private Long newCandidateId;
    /**
     * 平台业务时区复核时间。
     */
    private LocalDateTime reviewTime;
    /**
     * 新修订、候选和 Outbox 同事务完成时间。
     */
    private LocalDateTime executedTime;
    /**
     * 审批状态 CAS 版本。
     */
    private Long version;
    /**
     * 平台业务时区创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 平台业务时区最后更新时间。
     */
    private LocalDateTime updateTime;
}
