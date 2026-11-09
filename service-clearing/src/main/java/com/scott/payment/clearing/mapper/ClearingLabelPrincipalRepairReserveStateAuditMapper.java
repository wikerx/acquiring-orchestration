package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairReserveStateAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 历史标签本金修复保证金状态引用迁移审计 Mapper；审计只追加，不允许覆盖。
 */
public interface ClearingLabelPrincipalRepairReserveStateAuditMapper {

    /**
     * 追加保证金状态引用迁移前后的 HOLD 身份、版本和冻结快照。
     */
    @Insert("""
            INSERT INTO clearing_label_principal_repair_reserve_state_audit
            (repair_no, reserve_state_row_id, reserve_state_id, old_hold_detail_no,
             new_hold_detail_no, old_state_version, new_state_version, state_snapshot_hash,
             migrated_time, create_time)
            VALUES
            (#{row.repairNo}, #{row.reserveStateRowId}, #{row.reserveStateId},
             #{row.oldHoldDetailNo}, #{row.newHoldDetailNo}, #{row.oldStateVersion},
             #{row.newStateVersion}, #{row.stateSnapshotHash}, #{row.migratedTime},
             #{row.createTime})
            """)
    int insert(@Param("row") ClearingLabelPrincipalRepairReserveStateAuditDO row);
}
