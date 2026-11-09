package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史标签本金修复的候选依赖迁移审计，永久保留依赖迁移前后的候选主键。
 */
@Data
@TableName("clearing_label_principal_repair_dependency_audit")
public class ClearingLabelPrincipalRepairDependencyAuditDO {

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的历史标签本金修复单号。 */
    private String repairNo;
    /** 被迁移的既有依赖行主键。 */
    private Long dependencyId;
    /** 受依赖约束的保证金释放候选主键。 */
    private Long dependentCandidateId;
    /** 迁移前被依赖的旧清分候选主键。 */
    private Long oldDependsOnCandidateId;
    /** 迁移后被依赖的新清分候选主键。 */
    private Long newDependsOnCandidateId;
    /** 依赖类型，当前通道只允许 RESERVE_HOLD_POSTED。 */
    private String dependencyType;
    /** 迁移时冻结的保证金释放候选 CAS 版本。 */
    private Long dependentCandidateVersion;
    /** 依赖迁移完成时间。 */
    private LocalDateTime migratedTime;
    /** 审计行创建时间。 */
    private LocalDateTime createTime;
}
