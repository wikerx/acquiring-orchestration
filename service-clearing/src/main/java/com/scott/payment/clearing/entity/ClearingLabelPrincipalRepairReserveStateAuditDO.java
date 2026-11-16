package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史标签本金修复的保证金状态引用迁移审计，永久保留旧、新 HOLD 身份和状态版本。
 */
@Data
@TableName("clearing_label_principal_repair_reserve_state_audit")
public class ClearingLabelPrincipalRepairReserveStateAuditDO {

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的历史标签本金修复单号。 */
    private String repairNo;
    /** 被迁移的保证金状态物理主键。 */
    private Long reserveStateRowId;
    /** 保证金状态业务号。 */
    private String reserveStateId;
    /** 迁移前引用的旧 HOLD 明细号。 */
    private String oldHoldDetailNo;
    /** 迁移后引用的新 HOLD 明细号。 */
    private String newHoldDetailNo;
    /** 迁移前冻结的保证金状态 CAS 版本。 */
    private Long oldStateVersion;
    /** 迁移后的保证金状态 CAS 版本。 */
    private Long newStateVersion;
    /** 迁移前完整保证金状态的 SHA-256 快照。 */
    private String stateSnapshotHash;
    /** 状态引用迁移完成时间。 */
    private LocalDateTime migratedTime;
    /** 审计行创建时间。 */
    private LocalDateTime createTime;
}
