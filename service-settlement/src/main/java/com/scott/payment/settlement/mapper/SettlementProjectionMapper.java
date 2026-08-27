package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.entity.SettlementOperationIdentityDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;

/** 结算后交易投影任务 Mapper；资金事务只追加任务，不直接跨阶段修改交易查询投影。 */
public interface SettlementProjectionMapper {

    /** 批量追加候选级可靠投影任务，候选唯一键保证一笔只推进一次。 */
    @Insert("""
            <script>
            INSERT INTO settlement_projection_task
            (task_no, settlement_batch_no, projection_action, original_batch_no, candidate_id, transaction_id, transaction_date_time,
             clearing_revision, operation_id, merchant_id, settlement_currency, settlement_amount,
             settlement_date, task_status, retry_count, next_retry_time, version, create_time, update_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.taskNo}, #{row.settlementBatchNo}, #{row.projectionAction}, #{row.originalBatchNo}, #{row.candidateId}, #{row.transactionId},
                 #{row.transactionDateTime}, #{row.clearingRevision}, #{row.operationId},
                 #{row.merchantId}, #{row.settlementCurrency}, #{row.settlementAmount},
                 #{row.settlementDate}, #{row.taskStatus}, #{row.retryCount}, #{row.nextRetryTime},
                 #{row.version}, #{row.createTime}, #{row.updateTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertTasksIdempotent(@Param("rows") List<SettlementProjectionTaskDO> rows);

    /** 读取原批投影身份供冲正批复制，禁止重新按交易表逐条发现。 */
    @Select("""
            SELECT *
            FROM settlement_projection_task
            WHERE settlement_batch_no = #{settlementBatchNo}
            ORDER BY candidate_id ASC, id ASC
            """)
    List<SettlementProjectionTaskDO> selectTasksByBatch(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 锁定一条到期投影任务，多实例使用 SKIP LOCKED 并行处理不同交易。 */
    @Select("""
            SELECT *
            FROM settlement_projection_task
            WHERE task_status IN ('INIT', 'FAILED')
              AND next_retry_time <= #{now}
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    SettlementProjectionTaskDO selectNextDueForUpdate(@Param("now") LocalDateTime now);

    /** 锁内以版本 CAS 进入 PROCESSING；失败时整个投影事务回滚，再由独立事务记录退避。 */
    @Update("""
            UPDATE settlement_projection_task
            SET task_status = 'PROCESSING',
                version = version + 1,
                update_time = #{now}
            WHERE task_no = #{taskNo}
              AND task_status IN ('INIT', 'FAILED')
              AND version = #{expectedVersion}
            """)
    int markProcessing(@Param("taskNo") String taskNo,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("now") LocalDateTime now);

    /** 使用交易号和精确季度分片时间读取动作消息身份，不读取卡或账单信息。 */
    @Select("""
            SELECT transaction_id, operation_id, merchant_id, merchant_order_no,
                   transaction_type, transaction_status, settlement_status
            FROM transaction_operation
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    SettlementOperationIdentityDO selectOperationIdentity(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 按清分修订和 NOT_SETTLED 状态 CAS 投影权威财务结算结果。 */
    @Update("""
            UPDATE transaction_finance_state
            SET settlement_status = 'SETTLED',
                settlement_currency = #{settlementCurrency},
                settlement_amount = #{settlementAmount},
                settlement_date = #{settlementDate},
                settlement_batch_no = #{settlementBatchNo},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND clearing_status IN ('CLEARED', 'NOT_REQUIRED')
              AND settlement_status = 'NOT_SETTLED'
              AND deleted = 0
            """)
    int markFinanceStateSettled(@Param("transactionId") String transactionId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                @Param("clearingRevision") int clearingRevision,
                                @Param("settlementCurrency") String settlementCurrency,
                                @Param("settlementAmount") BigDecimal settlementAmount,
                                @Param("settlementDate") LocalDate settlementDate,
                                @Param("settlementBatchNo") String settlementBatchNo,
                                @Param("now") LocalDateTime now);

    /** 合法重放时验证财务状态已经由同一批次投影完成。 */
    @Select("""
            SELECT COUNT(1)
            FROM transaction_finance_state
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND settlement_status = 'SETTLED'
              AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount}
              AND settlement_date = #{settlementDate}
              AND settlement_batch_no = #{settlementBatchNo}
              AND deleted = 0
            """)
    int countMatchingSettledFinanceState(@Param("transactionId") String transactionId,
                                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                         @Param("clearingRevision") int clearingRevision,
                                         @Param("settlementCurrency") String settlementCurrency,
                                         @Param("settlementAmount") BigDecimal settlementAmount,
                                         @Param("settlementDate") LocalDate settlementDate,
                                         @Param("settlementBatchNo") String settlementBatchNo);

    /** 同步动作查询投影状态；余额事实仍只由 settlement 批次和资金流水决定。 */
    @Update("""
            UPDATE transaction_operation
            SET settlement_status = 'SETTLED',
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'NOT_SETTLED'
              AND deleted = 0
            """)
    int markOperationSettled(@Param("transactionId") String transactionId,
                             @Param("transactionDateTime") LocalDateTime transactionDateTime,
                             @Param("now") LocalDateTime now);

    /** 冲正投影只允许覆盖由原批次完成的 SETTLED 财务状态。 */
    @Update("""
            UPDATE transaction_finance_state
            SET settlement_status = 'REVERSED',
                settlement_batch_no = #{reversalBatchNo},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND settlement_status = 'SETTLED'
              AND settlement_batch_no = #{originalBatchNo}
              AND deleted = 0
            """)
    int markFinanceStateReversed(@Param("transactionId") String transactionId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("clearingRevision") int clearingRevision,
                                 @Param("originalBatchNo") String originalBatchNo,
                                 @Param("reversalBatchNo") String reversalBatchNo,
                                 @Param("now") LocalDateTime now);

    /** 冲正动作查询投影；财务状态中的批次引用提供最终身份校验。 */
    @Update("""
            UPDATE transaction_operation
            SET settlement_status = 'REVERSED',
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED'
              AND deleted = 0
            """)
    int markOperationReversed(@Param("transactionId") String transactionId,
                              @Param("transactionDateTime") LocalDateTime transactionDateTime,
                              @Param("now") LocalDateTime now);

    /** 结算状态投影和 FIFO 事件在同一事务落库。 */
    @Insert("""
            INSERT INTO settlement_event_outbox
            (event_no, settlement_batch_no, candidate_id, topic, tag, message_key,
             message_group, payload_json, event_status, retry_count, next_retry_time,
             version, create_time, update_time)
            VALUES
            (#{row.eventNo}, #{row.settlementBatchNo}, #{row.candidateId}, #{row.topic},
             #{row.tag}, #{row.messageKey}, #{row.messageGroup}, #{row.payloadJson},
             #{row.eventStatus}, #{row.retryCount}, #{row.nextRetryTime}, #{row.version},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertOutboxIdempotent(@Param("row") SettlementEventOutboxDO row);

    /** 投影、动作状态和 Outbox 均成功后完成任务。 */
    @Update("""
            UPDATE settlement_projection_task
            SET task_status = 'COMPLETED',
                completed_time = #{now},
                next_retry_time = NULL,
                last_failure_code = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE task_no = #{taskNo}
              AND task_status = 'PROCESSING'
              AND version = #{expectedVersion}
            """)
    int markCompleted(@Param("taskNo") String taskNo,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("now") LocalDateTime now);

    /**
     * 投影事务回滚后按原状态、重试次数和版本 CAS 记录失败，防止旧失败覆盖并发成功结果。
     */
    @Update("""
            UPDATE settlement_projection_task
            SET task_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                last_failure_code = #{failureCode},
                version = version + 1,
                update_time = #{now}
            WHERE task_no = #{taskNo}
              AND task_status IN ('INIT', 'FAILED')
              AND retry_count = #{expectedRetryCount}
              AND version = #{expectedVersion}
            """)
    int recordFailure(@Param("taskNo") String taskNo,
                      @Param("expectedRetryCount") int expectedRetryCount,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("failureCode") String failureCode,
                      @Param("nextRetryTime") LocalDateTime nextRetryTime,
                      @Param("now") LocalDateTime now);
}
