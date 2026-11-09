package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionItemDO;
import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionOrderDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史标签本金差额调整单和逐笔投影任务数据访问；所有状态迁移均使用当前状态及 version CAS。
 */
public interface SettlementPrincipalCorrectionMapper {

    /** 按 Maker 幂等键锁读已有调整单，用于同请求重放或冲突识别。 */
    @Select("""
            SELECT * FROM settlement_principal_correction_order
            WHERE submit_request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    SettlementPrincipalCorrectionOrderDO selectBySubmitRequestKeyForUpdate(
            @Param("requestKey") String requestKey);

    /** 按 Checker 幂等键锁读已有决策，避免重复审批或一键多义。 */
    @Select("""
            SELECT * FROM settlement_principal_correction_order
            WHERE decision_request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    SettlementPrincipalCorrectionOrderDO selectByDecisionRequestKeyForUpdate(
            @Param("requestKey") String requestKey);

    /** 按调整单号锁读当前权威状态。 */
    @Select("""
            SELECT * FROM settlement_principal_correction_order
            WHERE correction_no = #{correctionNo}
            LIMIT 1 FOR UPDATE
            """)
    SettlementPrincipalCorrectionOrderDO selectByCorrectionNoForUpdate(
            @Param("correctionNo") String correctionNo);

    /** 锁读原批次已有调整单；数据库唯一键最终保证一批一次修复。 */
    @Select("""
            SELECT * FROM settlement_principal_correction_order
            WHERE original_batch_no = #{batchNo}
            LIMIT 1 FOR UPDATE
            """)
    SettlementPrincipalCorrectionOrderDO selectByOriginalBatchForUpdate(
            @Param("batchNo") String batchNo);

    /** 追加待复核调整主单，不更新任何原资金或结算事实。 */
    @Insert("""
            INSERT INTO settlement_principal_correction_order
            (correction_no, submit_request_key, original_batch_no, merchant_id,
             settlement_account_id, target_currency, target_currency_exponent,
             original_batch_version, original_fund_ledger_id, item_count,
             correction_direction, correction_amount, source_fingerprint,
             correction_status, submitted_by_account_id, submitted_by_account_name,
             submitted_role_snapshot, submit_client_ip, submit_user_agent, submit_reason,
             submitted_time, projection_completed_count, version, create_time, update_time)
            VALUES
            (#{row.correctionNo}, #{row.submitRequestKey}, #{row.originalBatchNo}, #{row.merchantId},
             #{row.settlementAccountId}, #{row.targetCurrency}, #{row.targetCurrencyExponent},
             #{row.originalBatchVersion}, #{row.originalFundLedgerId}, #{row.itemCount},
             #{row.correctionDirection}, #{row.correctionAmount}, #{row.sourceFingerprint},
             #{row.correctionStatus}, #{row.submittedByAccountId}, #{row.submittedByAccountName},
             #{row.submittedRoleSnapshot}, #{row.submitClientIp}, #{row.submitUserAgent},
             #{row.submitReason}, #{row.submittedTime}, #{row.projectionCompletedCount},
             #{row.version}, #{row.createTime}, #{row.updateTime})
            """)
    int insertOrder(@Param("row") SettlementPrincipalCorrectionOrderDO row);

    /** 批量追加逐笔冻结事实及 WAITING_REVIEW 投影任务。 */
    @Insert("""
            <script>
            INSERT INTO settlement_principal_correction_item
            (correction_item_no, correction_no, original_result_item_id,
             original_projection_task_id, original_projection_task_no, candidate_id,
             clearing_detail_no, transaction_id, transaction_date_time, clearing_revision,
             operation_id, merchant_id, direction, original_source_amount,
             original_source_currency, original_source_currency_exponent, label_amount,
             label_currency, label_currency_exponent, original_locked_rate_id,
             original_locked_direct_rate, label_locked_rate_id, label_locked_direct_rate,
             target_currency, target_currency_exponent, rounding_mode, original_target_amount,
             expected_target_amount, signed_delta_amount, original_projection_amount,
             corrected_projection_amount, settlement_date, projection_status, retry_count,
             version, create_time, update_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.correctionItemNo}, #{row.correctionNo}, #{row.originalResultItemId},
                 #{row.originalProjectionTaskId}, #{row.originalProjectionTaskNo}, #{row.candidateId},
                 #{row.clearingDetailNo}, #{row.transactionId}, #{row.transactionDateTime},
                 #{row.clearingRevision}, #{row.operationId}, #{row.merchantId}, #{row.direction},
                 #{row.originalSourceAmount}, #{row.originalSourceCurrency},
                 #{row.originalSourceCurrencyExponent}, #{row.labelAmount}, #{row.labelCurrency},
                 #{row.labelCurrencyExponent}, #{row.originalLockedRateId},
                 #{row.originalLockedDirectRate}, #{row.labelLockedRateId},
                 #{row.labelLockedDirectRate},
                 #{row.targetCurrency}, #{row.targetCurrencyExponent}, #{row.roundingMode},
                 #{row.originalTargetAmount}, #{row.expectedTargetAmount}, #{row.signedDeltaAmount},
                 #{row.originalProjectionAmount}, #{row.correctedProjectionAmount},
                 #{row.settlementDate}, #{row.projectionStatus}, #{row.retryCount},
                 #{row.version}, #{row.createTime}, #{row.updateTime})
            </foreach>
            </script>
            """)
    int insertItems(@Param("rows") List<SettlementPrincipalCorrectionItemDO> rows);

    /** 按稳定明细顺序锁读全部冻结项，供审批前逐字段复核。 */
    @Select("""
            SELECT * FROM settlement_principal_correction_item
            WHERE correction_no = #{correctionNo}
            ORDER BY clearing_detail_no ASC, id ASC
            FOR UPDATE
            """)
    List<SettlementPrincipalCorrectionItemDO> selectItemsForUpdate(
            @Param("correctionNo") String correctionNo);

    /** 以当前状态和版本 CAS 拒绝调整单。 */
    @Update("""
            UPDATE settlement_principal_correction_order
            SET correction_status = 'REJECTED', decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent}, decision_action = 'REJECT',
                decision_request_key = #{row.decisionRequestKey},
                decision_comment = #{row.decisionComment}, decision_time = #{row.decisionTime},
                version = version + 1, update_time = #{row.decisionTime}
            WHERE correction_no = #{row.correctionNo}
              AND correction_status = 'PENDING_REVIEW' AND version = #{expectedVersion}
            """)
    int reject(@Param("row") SettlementPrincipalCorrectionOrderDO row,
               @Param("expectedVersion") long expectedVersion);

    /** 以当前状态和版本 CAS 标记已执行，并绑定唯一差额流水。 */
    @Update("""
            UPDATE settlement_principal_correction_order
            SET correction_status = 'EXECUTED', correction_ledger_id = #{row.correctionLedgerId},
                decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent}, decision_action = 'APPROVE',
                decision_request_key = #{row.decisionRequestKey},
                decision_comment = #{row.decisionComment}, decision_time = #{row.decisionTime},
                version = version + 1, update_time = #{row.decisionTime}
            WHERE correction_no = #{row.correctionNo}
              AND correction_status = 'PENDING_REVIEW' AND version = #{expectedVersion}
            """)
    int execute(@Param("row") SettlementPrincipalCorrectionOrderDO row,
                @Param("expectedVersion") long expectedVersion);

    /** 拒绝主单时将全部未激活冻结项原子取消。 */
    @Update("""
            UPDATE settlement_principal_correction_item
            SET projection_status = 'CANCELLED', version = version + 1, update_time = #{now}
            WHERE correction_no = #{correctionNo} AND projection_status = 'WAITING_REVIEW'
            """)
    int cancelItems(@Param("correctionNo") String correctionNo,
                    @Param("now") LocalDateTime now);

    /** 批准入账后激活全部冻结投影任务，并设置首次处理时间。 */
    @Update("""
            UPDATE settlement_principal_correction_item
            SET projection_status = 'INIT', next_retry_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE correction_no = #{correctionNo} AND projection_status = 'WAITING_REVIEW'
            """)
    int activateItems(@Param("correctionNo") String correctionNo,
                      @Param("now") LocalDateTime now);

    /** 多实例使用 SKIP LOCKED 领取一条已执行主单下的到期任务。 */
    @Select("""
            SELECT item.*
            FROM settlement_principal_correction_item item
            INNER JOIN settlement_principal_correction_order correction
                    ON correction.correction_no = item.correction_no
                   AND correction.correction_status = 'EXECUTED'
            WHERE item.projection_status IN ('INIT', 'FAILED')
              AND item.next_retry_time <= #{now}
            ORDER BY item.next_retry_time ASC, item.id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    SettlementPrincipalCorrectionItemDO selectNextProjectionForUpdate(
            @Param("now") LocalDateTime now);

    /** 以原状态和版本 CAS 认领投影任务；主事务失败时该更新一并回滚。 */
    @Update("""
            UPDATE settlement_principal_correction_item
            SET projection_status = 'PROCESSING', version = version + 1, update_time = #{now}
            WHERE correction_item_no = #{itemNo}
              AND projection_status IN ('INIT', 'FAILED') AND version = #{expectedVersion}
            """)
    int markProjectionProcessing(@Param("itemNo") String itemNo,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("now") LocalDateTime now);

    /** 三层投影全部成功后以 PROCESSING 状态和版本 CAS 完成任务。 */
    @Update("""
            UPDATE settlement_principal_correction_item
            SET projection_status = 'COMPLETED', completed_time = #{now},
                next_retry_time = NULL, last_failure_code = NULL,
                version = version + 1, update_time = #{now}
            WHERE correction_item_no = #{itemNo}
              AND projection_status = 'PROCESSING' AND version = #{expectedVersion}
            """)
    int markProjectionCompleted(@Param("itemNo") String itemNo,
                                @Param("expectedVersion") long expectedVersion,
                                @Param("now") LocalDateTime now);

    /** 单条任务完成后递增主单完成数，且不允许超过冻结总数。 */
    @Update("""
            UPDATE settlement_principal_correction_order
            SET projection_completed_count = projection_completed_count + 1,
                update_time = #{now}
            WHERE correction_no = #{correctionNo} AND correction_status = 'EXECUTED'
              AND projection_completed_count < item_count
            """)
    int incrementProjectionCompleted(@Param("correctionNo") String correctionNo,
                                     @Param("now") LocalDateTime now);

    /** 主事务回滚后按原版本和失败次数记录下一次退避，不覆盖并发完成。 */
    @Update("""
            UPDATE settlement_principal_correction_item
            SET projection_status = 'FAILED', retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime}, last_failure_code = #{failureCode},
                version = version + 1, update_time = #{now}
            WHERE correction_item_no = #{itemNo}
              AND projection_status IN ('INIT', 'FAILED')
              AND retry_count = #{expectedRetryCount} AND version = #{expectedVersion}
            """)
    int recordProjectionFailure(@Param("itemNo") String itemNo,
                                @Param("expectedRetryCount") int expectedRetryCount,
                                @Param("expectedVersion") long expectedVersion,
                                @Param("failureCode") String failureCode,
                                @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                @Param("now") LocalDateTime now);
}
