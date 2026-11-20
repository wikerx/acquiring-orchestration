package com.scott.payment.settlement.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 历史本金修复的只读来源证明，联合已存在的迁移审计、修复单和当前依赖。
 */
@Data
public class SettlementReserveHoldMigrationDO {
    /** 已执行修复单号。 */
    private String repairNo;
    /** 审计中的旧 HOLD 业务号。 */
    private String oldHoldDetailNo;
    /** 审计中的新 HOLD 业务号。 */
    private String newHoldDetailNo;
    /** 被迁移的清分保证金状态业务号。 */
    private String reserveStateId;
    /** 迁移前状态版本。 */
    private Long oldStateVersion;
    /** 迁移后状态版本。 */
    private Long newStateVersion;
    /** 修复单当前状态。 */
    private String repairStatus;
    /** 修复单所属商户。 */
    private String merchantId;
    /** 原支付号。 */
    private String transactionId;
    /** 原支付精确分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 原支付清分状态号。 */
    private String financeStateId;
    /** 被替代的清分修订。 */
    private Integer oldRevision;
    /** 修复后的清分修订。 */
    private Integer newRevision;
    /** 修复单冻结的 HOLD 数量。 */
    private Integer reserveFactCount;
    /** 修复单冻结的保证金状态数量。 */
    private Integer reserveStateCount;
    /** 修复单冻结的依赖数量。 */
    private Integer dependencyCount;
    /** 被替代的旧清分候选。 */
    private Long oldCandidateId;
    /** 修复后的新清分候选。 */
    private Long newCandidateId;
    /** 迁移审计指向的原 RELEASE 候选。 */
    private Long dependentCandidateId;
    /** 迁移前被依赖候选。 */
    private Long oldDependsOnCandidateId;
    /** 迁移后被依赖候选。 */
    private Long newDependsOnCandidateId;
    /** 迁移审计的依赖类型。 */
    private String dependencyType;
    /** 当前依赖行所属候选。 */
    private Long currentDependentCandidateId;
    /** 当前依赖行目标候选。 */
    private Long currentDependsOnCandidateId;
    /** 当前依赖类型。 */
    private String currentDependencyType;
    /** 新清分候选状态。 */
    private String newCandidateStatus;
    /** 新清分候选来源类型。 */
    private String newCandidateSourceType;
    /** 新清分候选的财务状态号。 */
    private String newCandidateSourceBusinessId;
    /** 新清分候选的原支付号。 */
    private String newCandidateTransactionId;
    /** 新清分候选精确分片时间。 */
    private LocalDateTime newCandidateTransactionDateTime;
    /** 新清分候选修订。 */
    private Integer newCandidateRevision;
    /** 新清分候选商户。 */
    private String newCandidateMerchantId;
    /** 新清分候选结算资料。 */
    private Long newCandidateProfileId;
    /** 新清分候选目标币种。 */
    private String newCandidateTargetCurrency;
    /** 新清分候选目标币种精度。 */
    private Integer newCandidateTargetCurrencyExponent;
}
