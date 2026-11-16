package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 标签本金修复审批 Mapper；申请只追加，复核终态只允许从 PENDING_REVIEW 版本 CAS。
 */
public interface ClearingLabelPrincipalRepairMapper {

    /**
     * 以请求键幂等追加修复申请，不覆盖既有审批事实。
     */
    @Insert("""
            INSERT INTO clearing_label_principal_repair
            (repair_no, request_key, transaction_id, transaction_date_time, finance_state_id,
             merchant_id, expected_finance_state_version, expected_clearing_revision,
             candidate_id, expected_candidate_version, principal_detail_no,
             principal_amount_before, principal_currency_before, principal_currency_exponent_before,
             label_amount, label_currency, label_currency_exponent, source_snapshot_hash,
             reserve_fact_count, reserve_snapshot_hash, reserve_state_count,
             reserve_state_snapshot_hash, dependency_count, dependency_snapshot_hash,
             reason, submit_operator, repair_status, version, create_time, update_time)
            VALUES
            (#{row.repairNo}, #{row.requestKey}, #{row.transactionId}, #{row.transactionDateTime},
             #{row.financeStateId}, #{row.merchantId}, #{row.expectedFinanceStateVersion},
             #{row.expectedClearingRevision}, #{row.candidateId}, #{row.expectedCandidateVersion},
             #{row.principalDetailNo}, #{row.principalAmountBefore}, #{row.principalCurrencyBefore},
             #{row.principalCurrencyExponentBefore}, #{row.labelAmount}, #{row.labelCurrency},
             #{row.labelCurrencyExponent}, #{row.sourceSnapshotHash}, #{row.reserveFactCount},
             #{row.reserveSnapshotHash}, #{row.reserveStateCount}, #{row.reserveStateSnapshotHash},
             #{row.dependencyCount}, #{row.dependencySnapshotHash}, #{row.reason},
             #{row.submitOperator}, #{row.repairStatus}, #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") ClearingLabelPrincipalRepairDO row);

    /**
     * 按请求幂等键锁定既有申请，供重放时核对全部冻结身份。
     */
    @Select("""
            SELECT * FROM clearing_label_principal_repair
            WHERE request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    ClearingLabelPrincipalRepairDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 按修复单号锁定申请，串行化双人复核和执行。
     */
    @Select("""
            SELECT * FROM clearing_label_principal_repair
            WHERE repair_no = #{repairNo}
            LIMIT 1 FOR UPDATE
            """)
    ClearingLabelPrincipalRepairDO selectForUpdate(@Param("repairNo") String repairNo);

    /**
     * Checker 拒绝待复核申请，不生成清分修订或结算候选。
     */
    @Update("""
            UPDATE clearing_label_principal_repair
            SET repair_status = 'REJECTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                version = version + 1, update_time = #{reviewTime}
            WHERE repair_no = #{repairNo}
              AND repair_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markRejected(@Param("repairNo") String repairNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime);

    /**
     * 新修订、候选和 Outbox 已提交后原子终结修复申请。
     */
    @Update("""
            UPDATE clearing_label_principal_repair
            SET repair_status = 'EXECUTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                new_clearing_revision = #{newRevision}, new_candidate_no = #{newCandidateNo},
                new_candidate_id = #{newCandidateId},
                executed_time = #{executedTime}, version = version + 1, update_time = #{executedTime}
            WHERE repair_no = #{repairNo}
              AND repair_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markExecuted(@Param("repairNo") String repairNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime,
                     @Param("newRevision") int newRevision,
                     @Param("newCandidateNo") String newCandidateNo,
                     @Param("newCandidateId") long newCandidateId,
                     @Param("executedTime") LocalDateTime executedTime);
}
