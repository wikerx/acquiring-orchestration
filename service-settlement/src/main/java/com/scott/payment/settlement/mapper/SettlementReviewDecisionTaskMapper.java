package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewDecisionTaskDO;
import com.scott.payment.settlement.entity.SettlementReviewDecisionRecoveryAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 大批量预审异步决策的任务、租约和进度数据访问。 */
public interface SettlementReviewDecisionTaskMapper {

    @Insert("""
            INSERT INTO settlement_review_decision_task
            (task_no, request_key, review_order_no, expected_review_version,
             decision_action, decision_comment, operator_account_id, operator_account_name,
             operator_role_snapshot, client_ip, user_agent, operation_time,
             total_segment_count, processed_segment_count, result_batch_count,
             first_settlement_batch_no, task_status, retry_count, next_retry_time,
             version, create_time, update_time)
            VALUES
            (#{row.taskNo}, #{row.requestKey}, #{row.reviewOrderNo}, #{row.expectedReviewVersion},
             #{row.decisionAction}, #{row.decisionComment}, #{row.operatorAccountId},
             #{row.operatorAccountName}, #{row.operatorRoleSnapshot}, #{row.clientIp},
             #{row.userAgent}, #{row.operationTime}, #{row.totalSegmentCount}, 0, 0,
             NULL, 'QUEUED', 0, #{row.nextRetryTime}, 0, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementReviewDecisionTaskDO row);

    @Select("SELECT * FROM settlement_review_decision_task WHERE request_key = #{requestKey} LIMIT 1 FOR UPDATE")
    SettlementReviewDecisionTaskDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    @Select("SELECT * FROM settlement_review_decision_task WHERE task_no = #{taskNo} LIMIT 1")
    SettlementReviewDecisionTaskDO selectByTaskNo(@Param("taskNo") String taskNo);

    @Select("SELECT * FROM settlement_review_decision_task WHERE task_no = #{taskNo} LIMIT 1 FOR UPDATE")
    SettlementReviewDecisionTaskDO selectByTaskNoForUpdate(@Param("taskNo") String taskNo);

    @Select("""
            SELECT * FROM settlement_review_decision_recovery_audit
            WHERE request_key = #{requestKey} LIMIT 1
            """)
    SettlementReviewDecisionRecoveryAuditDO selectRecoveryAuditByRequestKey(
            @Param("requestKey") String requestKey);

    @Select("""
            SELECT * FROM settlement_review_decision_recovery_audit
            WHERE request_key = #{requestKey} LIMIT 1 FOR UPDATE
            """)
    SettlementReviewDecisionRecoveryAuditDO selectRecoveryAuditByRequestKeyForUpdate(
            @Param("requestKey") String requestKey);

    @Insert("""
            INSERT INTO settlement_review_decision_recovery_audit
            (task_no, review_order_no, request_key, expected_version, task_status_before,
             processed_segment_count_before, result_batch_count_before, retry_count_before,
             failure_code_before, failure_message_before, started_time_before,
             completed_time_before, operator_account_id,
             operator_account_name, operator_role_snapshot, client_ip, user_agent, reason,
             operation_time, recovered_time, create_time)
            VALUES
            (#{row.taskNo}, #{row.reviewOrderNo}, #{row.requestKey}, #{row.expectedVersion},
             #{row.taskStatusBefore}, #{row.processedSegmentCountBefore},
             #{row.resultBatchCountBefore}, #{row.retryCountBefore}, #{row.failureCodeBefore},
             #{row.failureMessageBefore}, #{row.startedTimeBefore}, #{row.completedTimeBefore},
             #{row.operatorAccountId}, #{row.operatorAccountName},
             #{row.operatorRoleSnapshot}, #{row.clientIp}, #{row.userAgent}, #{row.reason},
             #{row.operationTime}, #{row.recoveredTime}, #{row.createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertRecoveryAuditIdempotent(
            @Param("row") SettlementReviewDecisionRecoveryAuditDO row);

    @Select("""
            SELECT * FROM settlement_review_decision_task
            WHERE task_status IN ('QUEUED', 'PROCESSING')
              AND next_retry_time <= #{now}
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    SettlementReviewDecisionTaskDO selectNextDueForUpdate(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'PROCESSING', processing_owner = #{owner},
                processing_deadline = #{deadline}, started_time = COALESCE(started_time, #{now}),
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status IN ('QUEUED', 'PROCESSING')
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
              AND version = #{version}
            """)
    int markProcessing(@Param("taskNo") String taskNo,
                       @Param("version") long version,
                       @Param("owner") String owner,
                       @Param("deadline") LocalDateTime deadline,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET processed_segment_count = processed_segment_count + 1,
                result_batch_count = result_batch_count + #{resultBatchDelta},
                first_settlement_batch_no = COALESCE(first_settlement_batch_no, #{settlementBatchNo}),
                processing_owner = NULL, processing_deadline = NULL, next_retry_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING'
              AND processing_owner = #{owner} AND version = #{version}
              AND #{resultBatchDelta} IN (0, 1)
            """)
    int advance(@Param("taskNo") String taskNo,
                @Param("version") long version,
                @Param("owner") String owner,
                @Param("resultBatchDelta") int resultBatchDelta,
                @Param("settlementBatchNo") String settlementBatchNo,
                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'COMPLETED', completed_time = #{now}, processing_owner = NULL,
                processing_deadline = NULL, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING'
              AND processing_owner = #{owner} AND version = #{version}
              AND processed_segment_count = total_segment_count
            """)
    int markCompleted(@Param("taskNo") String taskNo,
                      @Param("version") long version,
                      @Param("owner") String owner,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'QUEUED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL,
                next_retry_time = #{nextRetryTime}, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING' AND version = #{version}
            """)
    int reschedule(@Param("taskNo") String taskNo,
                   @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'FAILED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL, completed_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING' AND version = #{version}
            """)
    int markFailed(@Param("taskNo") String taskNo,
                   @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'QUEUED', processing_owner = NULL, processing_deadline = NULL,
                retry_count = 0, next_retry_time = #{now}, last_failure_code = NULL,
                last_failure_message = NULL, started_time = NULL, completed_time = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'FAILED' AND version = #{expectedVersion}
            """)
    int resumeFailed(@Param("taskNo") String taskNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("now") LocalDateTime now);
}
