package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDependencyAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 历史标签本金修复依赖迁移审计 Mapper；审计只追加，不允许覆盖。
 */
public interface ClearingLabelPrincipalRepairDependencyAuditMapper {

    /**
     * 追加依赖迁移前后身份和版本快照。
     */
    @Insert("""
            INSERT INTO clearing_label_principal_repair_dependency_audit
            (repair_no, dependency_id, dependent_candidate_id, old_depends_on_candidate_id,
             new_depends_on_candidate_id, dependency_type, dependent_candidate_version,
             migrated_time, create_time)
            VALUES
            (#{row.repairNo}, #{row.dependencyId}, #{row.dependentCandidateId},
             #{row.oldDependsOnCandidateId}, #{row.newDependsOnCandidateId},
             #{row.dependencyType}, #{row.dependentCandidateVersion},
             #{row.migratedTime}, #{row.createTime})
            """)
    int insert(@Param("row") ClearingLabelPrincipalRepairDependencyAuditDO row);
}
