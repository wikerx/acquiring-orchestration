package com.scott.payment.clearing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.FinanceSummary;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionFinanceStateMapper
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分权威状态 Mapper，只提供精确分片读取和带版本条件的状态 CAS，不承载业务判断。
 * @status : create
 */
public interface ClearingTransactionFinanceStateMapper extends BaseMapper<ClearingTransactionFinanceStateDO> {

    /** 单季度管理查询，使用 transaction_date_time + id 主键游标，禁止 OFFSET。 */
    @Select("""
            <script>
            SELECT *
            FROM transaction_finance_state
            WHERE transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTime}
              AND deleted = 0
              <if test='merchantId != null and merchantId != ""'>
                AND merchant_id = #{merchantId}
              </if>
              <if test='transactionId != null and transactionId != ""'>
                AND transaction_id = #{transactionId}
              </if>
              <if test='clearingStatus != null and clearingStatus != ""'>
                AND clearing_status = #{clearingStatus}
              </if>
              <if test='cursorTransactionDateTime != null and cursorId != null'>
                AND (transaction_date_time &gt; #{cursorTransactionDateTime}
                     OR (transaction_date_time = #{cursorTransactionDateTime} AND id &gt; #{cursorId}))
              </if>
            ORDER BY transaction_date_time ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ClearingTransactionFinanceStateDO> selectForManagementSearch(
            @Param("merchantId") String merchantId,
            @Param("transactionId") String transactionId,
            @Param("clearingStatus") String clearingStatus,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("cursorTransactionDateTime") LocalDateTime cursorTransactionDateTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /**
     * 按动作号和分片时间读取清分状态。
     *
     * @param transactionId 动作级平台交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 动作财务状态；不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_finance_state
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    ClearingTransactionFinanceStateDO selectByTransaction(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 首次终态事件到达时幂等创建动作级 finance state，唯一 transaction_id 防止重复动作状态。 */
    @Insert("""
            INSERT INTO transaction_finance_state
            (finance_state_id, transaction_id, operation_id, merchant_id, source_transaction_id,
             label_currency, transaction_type, clearing_status, clearing_revision,
             settlement_status, reconciliation_status, accounting_status,
             transaction_date_time, transaction_utc_time, transaction_time_zone,
             version, deleted, create_time, update_time)
            VALUES
            (#{financeStateId}, #{operation.transactionId}, #{operation.operationId},
             #{operation.merchantId}, #{operation.sourceTransactionId}, #{operation.labelCurrency},
             #{operation.transactionType}, 'PENDING', 0, 'NOT_SETTLED', 'NOT_RECONCILED',
             'NOT_ACCOUNTED', #{operation.transactionDateTime}, #{operation.transactionUtcTime},
             #{operation.transactionTimeZone}, 0, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("financeStateId") String financeStateId,
                       @Param("operation") ClearingTransactionOperationDO operation,
                       @Param("now") LocalDateTime now);

    /** 阶段B按当前动作分片时间锁定 PROCESSING finance state。 */
    @Select("""
            SELECT *
            FROM transaction_finance_state
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    ClearingTransactionFinanceStateDO selectForUpdate(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 从自动可重试状态领取 PROCESSING 租约；终态、人工复核和未过期 PROCESSING 均不会被覆盖。
     *
     * @param transactionId 动作级平台交易号
     * @param transactionDateTime 动作季度分片时间
     * @param expectedVersion 当前读取到的版本
     * @param processingOwner 处理实例和线程标识
     * @param startTime 本次领取时间
     * @param processingDeadline 租约截止时间
     * @param triggerEventNo 首次触发事件号
     * @return 1 表示领取成功，0 表示状态或版本已经变化
     */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = 'PROCESSING',
                clearing_trigger_event_no = COALESCE(clearing_trigger_event_no, #{triggerEventNo}),
                clearing_request_time = COALESCE(clearing_request_time, #{startTime}),
                clearing_start_time = #{startTime},
                processing_owner = #{processingOwner},
                processing_deadline = #{processingDeadline},
                next_retry_time = NULL,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND (
                    clearing_status IN ('NOT_CLEARED', 'PENDING', 'WAITING_SOURCE', 'FAILED')
                    OR (clearing_status = 'PROCESSING' AND processing_deadline <= #{startTime})
                  )
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int claimProcessing(@Param("transactionId") String transactionId,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("expectedVersion") int expectedVersion,
                        @Param("processingOwner") String processingOwner,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("processingDeadline") LocalDateTime processingDeadline,
                        @Param("triggerEventNo") String triggerEventNo);

    /**
     * 校验 owner、租约、状态和版本后完成动作清分摘要；结算汇率和结算金额兼容列始终不写。
     */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = #{summary.clearingStatus},
                clearing_revision = #{summary.clearingRevision},
                clearing_complete_time = #{now},
                processing_owner = NULL,
                processing_deadline = NULL,
                next_retry_time = NULL,
                last_failure_code = NULL,
                last_failure_message = NULL,
                fee_plan_id = #{summary.feePlanId},
                fee_plan_version_id = #{summary.feePlanVersionId},
                fee_plan_version_no = #{summary.feePlanVersionNo},
                fee_snapshot_hash = #{summary.feeSnapshotHash},
                gross_label_amount = #{summary.grossLabelAmount},
                fee_component_currency_count = #{summary.feeComponentCurrencyCount},
                fee_evaluation_status = #{summary.feeEvaluationStatus},
                settlement_currency = #{summary.settlementCurrency},
                settlement_eligible_date = #{summary.settlementEligibleDate},
                platform_fee_currency = CASE
                    WHEN #{summary.platformFeeAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                platform_fee_amount = #{summary.platformFeeAmount},
                fee_reversal_amount = #{summary.feeReversalAmount},
                merchant_receivable_currency = CASE
                    WHEN #{summary.merchantReceivableAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                merchant_receivable_amount = #{summary.merchantReceivableAmount},
                reserve_currency = CASE
                    WHEN #{summary.reserveAmount} IS NULL AND #{summary.reserveReversalAmount} IS NULL
                    THEN NULL ELSE #{summary.labelCurrency} END,
                reserve_amount = #{summary.reserveAmount},
                reserve_reversal_amount = #{summary.reserveReversalAmount},
                expected_reserve_release_date = #{summary.expectedReserveReleaseDate},
                net_settlement_currency = CASE
                    WHEN #{summary.netSettlementAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                net_settlement_amount = #{summary.netSettlementAmount},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status = 'PROCESSING'
              AND processing_owner = #{processingOwner}
              AND processing_deadline >= #{now}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int completeProcessing(@Param("transactionId") String transactionId,
                           @Param("transactionDateTime") LocalDateTime transactionDateTime,
                           @Param("processingOwner") String processingOwner,
                           @Param("expectedVersion") int expectedVersion,
                           @Param("summary") FinanceSummary summary,
                           @Param("now") LocalDateTime now);

    /** 使用领取版本记录失败、等待源动作或人工复核状态，禁止覆盖已完成终态。 */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = #{targetStatus},
                clearing_retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                last_failure_code = #{failureCode},
                last_failure_message = #{failureMessage},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status = 'PROCESSING'
              AND processing_owner = #{processingOwner}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int recordFailure(@Param("transactionId") String transactionId,
                      @Param("transactionDateTime") LocalDateTime transactionDateTime,
                      @Param("processingOwner") String processingOwner,
                      @Param("expectedVersion") int expectedVersion,
                      @Param("targetStatus") String targetStatus,
                      @Param("retryCount") int retryCount,
                      @Param("nextRetryTime") LocalDateTime nextRetryTime,
                      @Param("failureCode") String failureCode,
                      @Param("failureMessage") String failureMessage,
                      @Param("now") LocalDateTime now);

    /**
     * 补偿任务对仍然匹配候选快照的状态安排立即重试；不覆盖完成态和人工复核态。
     */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = 'FAILED',
                clearing_retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                last_failure_code = #{failureCode},
                last_failure_message = #{failureMessage},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND (
                    #{expectedDeadline} IS NULL
                    OR processing_deadline = #{expectedDeadline}
                  )
              AND deleted = 0
            """)
    int scheduleCompensationRetry(@Param("transactionId") String transactionId,
                                  @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                  @Param("expectedStatus") String expectedStatus,
                                  @Param("expectedVersion") int expectedVersion,
                                  @Param("expectedDeadline") LocalDateTime expectedDeadline,
                                  @Param("retryCount") int retryCount,
                                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                  @Param("failureCode") String failureCode,
                                  @Param("failureMessage") String failureMessage,
                                  @Param("now") LocalDateTime now);

    /** 补偿达到重试上限时以版本 CAS 进入人工复核，禁止继续发布延时消息。 */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = 'MANUAL_REVIEW',
                next_retry_time = NULL,
                last_failure_code = 'CLEARING_RETRY_EXHAUSTED',
                last_failure_message = #{failureMessage},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND (
                    #{expectedDeadline} IS NULL
                    OR processing_deadline = #{expectedDeadline}
                  )
              AND deleted = 0
            """)
    int escalateCompensationReview(@Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("expectedStatus") String expectedStatus,
                                   @Param("expectedVersion") int expectedVersion,
                                   @Param("expectedDeadline") LocalDateTime expectedDeadline,
                                   @Param("failureMessage") String failureMessage,
                                   @Param("now") LocalDateTime now);

    /** 人工重试以预期版本从失败、等待或人工复核状态重新安排一次受控延时消息。 */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = 'FAILED',
                clearing_retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                last_failure_code = 'CLEARING_MANUAL_RETRY',
                last_failure_message = #{reason},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status IN ('PENDING', 'FAILED', 'WAITING_SOURCE', 'MANUAL_REVIEW')
              AND settlement_status = 'NOT_SETTLED'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int scheduleManualRetry(@Param("transactionId") String transactionId,
                            @Param("transactionDateTime") LocalDateTime transactionDateTime,
                            @Param("expectedVersion") int expectedVersion,
                            @Param("retryCount") int retryCount,
                            @Param("nextRetryTime") LocalDateTime nextRetryTime,
                            @Param("reason") String reason,
                            @Param("now") LocalDateTime now);

    /** 将未完成且无活动租约的动作升级到人工复核，完成态和已结算状态不可覆盖。 */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = 'MANUAL_REVIEW',
                next_retry_time = NULL,
                last_failure_code = #{failureCode},
                last_failure_message = #{reason},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status IN ('NOT_CLEARED', 'PENDING', 'FAILED', 'WAITING_SOURCE', 'PROCESSING')
              AND (clearing_status &lt;&gt; 'PROCESSING' OR processing_deadline &lt;= #{now})
              AND settlement_status = 'NOT_SETTLED'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markManualReview(@Param("transactionId") String transactionId,
                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                         @Param("expectedVersion") int expectedVersion,
                         @Param("failureCode") String failureCode,
                         @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    /**
     * 未结算完成态重算的最终 CAS。整个旧修订在此之前仍保持有效，失败由事务回滚。
     */
    @Update("""
            UPDATE transaction_finance_state
            SET clearing_status = #{summary.clearingStatus},
                clearing_revision = #{summary.clearingRevision},
                clearing_complete_time = #{now},
                next_retry_time = NULL,
                last_failure_code = NULL,
                last_failure_message = NULL,
                fee_plan_id = #{summary.feePlanId},
                fee_plan_version_id = #{summary.feePlanVersionId},
                fee_plan_version_no = #{summary.feePlanVersionNo},
                fee_snapshot_hash = #{summary.feeSnapshotHash},
                gross_label_amount = #{summary.grossLabelAmount},
                fee_component_currency_count = #{summary.feeComponentCurrencyCount},
                fee_evaluation_status = #{summary.feeEvaluationStatus},
                settlement_currency = #{summary.settlementCurrency},
                settlement_eligible_date = #{summary.settlementEligibleDate},
                platform_fee_currency = CASE
                    WHEN #{summary.platformFeeAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                platform_fee_amount = #{summary.platformFeeAmount},
                fee_reversal_amount = #{summary.feeReversalAmount},
                merchant_receivable_currency = CASE
                    WHEN #{summary.merchantReceivableAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                merchant_receivable_amount = #{summary.merchantReceivableAmount},
                reserve_currency = CASE
                    WHEN #{summary.reserveAmount} IS NULL AND #{summary.reserveReversalAmount} IS NULL
                    THEN NULL ELSE #{summary.labelCurrency} END,
                reserve_amount = #{summary.reserveAmount},
                reserve_reversal_amount = #{summary.reserveReversalAmount},
                expected_reserve_release_date = #{summary.expectedReserveReleaseDate},
                net_settlement_currency = CASE
                    WHEN #{summary.netSettlementAmount} IS NULL THEN NULL ELSE #{summary.labelCurrency} END,
                net_settlement_amount = #{summary.netSettlementAmount},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_status IN ('CLEARED', 'NOT_REQUIRED')
              AND clearing_revision = #{expectedRevision}
              AND settlement_status = 'NOT_SETTLED'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int completeRecalculation(@Param("transactionId") String transactionId,
                              @Param("transactionDateTime") LocalDateTime transactionDateTime,
                              @Param("expectedVersion") int expectedVersion,
                              @Param("expectedRevision") int expectedRevision,
                              @Param("summary") FinanceSummary summary,
                              @Param("now") LocalDateTime now);
}
