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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProjectionMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算后交易投影数据访问接口；资金事务只追加真实交易任务，异步阶段按分片时间和修订 CAS 同步交易主单、动作单并写 Outbox。
 * @status : create
 */
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

    /**
     * 读取原批投影身份供冲正批复制，禁止重新按交易表逐条发现。
     * @param settlementBatchNo 原正式结算批次号
     * @return 按候选 ID 排序的原投影任务集合
     */
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

    /**
     * 锁内以版本 CAS 进入 PROCESSING；失败时整个投影事务回滚，再由独立事务记录退避。
     * @param taskNo 投影任务号
     * @param expectedVersion 认领前任务版本
     * @param now 认领时间
     * @return 成功更新行数，必须为 1
     */
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

    /**
     * 使用动作分片时间和固定定位表读取动作、币种及生命周期主单分片身份。
     * @param transactionId 平台交易号
     * @param transactionDateTime 动作交易分片时间
     * @return 最小非敏感投影身份，不存在时为空
     */
    @Select("""
            SELECT operation.transaction_id, operation.operation_id, operation.merchant_id,
                   operation.merchant_order_no, operation.transaction_type,
                   operation.transaction_status, operation.transaction_currency,
                   operation.settlement_status, locator.root_transaction_date_time
            FROM transaction_operation operation
            JOIN transaction_locator locator
              ON locator.transaction_id = operation.transaction_id
             AND locator.merchant_id = operation.merchant_id
            WHERE operation.transaction_id = #{transactionId}
              AND operation.transaction_date_time = #{transactionDateTime}
              AND operation.deleted = 0
            LIMIT 1
            """)
    SettlementOperationIdentityDO selectOperationIdentity(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 读取批次冻结的来源币种到目标币种直接汇率。
     * @param settlementBatchNo 正式结算批次号
     * @param sourceCurrency 来源 ISO 币种
     * @param targetCurrency 目标 ISO 币种
     * @return LOCKED 汇率，不存在时为空
     */
    @Select("""
            SELECT direct_rate FROM settlement_batch_rate
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND source_currency = #{sourceCurrency} AND target_currency = #{targetCurrency}
              AND rate_type = 'SETTLEMENT' AND rate_status = 'LOCKED'
            LIMIT 1
            """)
    BigDecimal selectDirectSettlementRate(@Param("settlementBatchNo") String settlementBatchNo,
                                          @Param("sourceCurrency") String sourceCurrency,
                                          @Param("targetCurrency") String targetCurrency);

    /**
     * 按交易分片、清分修订及 NOT_SETTLED 前置状态写入财务状态结算字段。
     * @return 成功更新行数，0 时必须回读区分合法重放与状态冲突
     */
    @Update("""
            UPDATE transaction_finance_state
            SET settlement_status = 'SETTLED', settlement_currency = #{settlementCurrency},
                settlement_rate = #{settlementRate}, settlement_amount = #{settlementAmount},
                settlement_date = #{settlementDate}, settlement_batch_no = #{settlementBatchNo},
                version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND clearing_status IN ('CLEARED', 'NOT_REQUIRED')
              AND settlement_status = 'NOT_SETTLED' AND deleted = 0
            """)
    int markFinanceStateSettled(@Param("transactionId") String transactionId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                @Param("clearingRevision") int clearingRevision,
                                @Param("settlementCurrency") String settlementCurrency,
                                @Param("settlementRate") BigDecimal settlementRate,
                                @Param("settlementAmount") BigDecimal settlementAmount,
                                @Param("settlementDate") LocalDate settlementDate,
                                @Param("settlementBatchNo") String settlementBatchNo,
                                @Param("now") LocalDateTime now);

    /**
     * 校验财务状态是否已具有完全一致的批次、币种、汇率、金额和日期。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_finance_state
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision} AND settlement_status = 'SETTLED'
              AND settlement_currency = #{settlementCurrency} AND settlement_rate = #{settlementRate}
              AND settlement_amount = #{settlementAmount} AND settlement_date = #{settlementDate}
              AND settlement_batch_no = #{settlementBatchNo} AND deleted = 0
            """)
    int countMatchingSettledFinanceState(@Param("transactionId") String transactionId,
                                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                         @Param("clearingRevision") int clearingRevision,
                                         @Param("settlementCurrency") String settlementCurrency,
                                         @Param("settlementRate") BigDecimal settlementRate,
                                         @Param("settlementAmount") BigDecimal settlementAmount,
                                         @Param("settlementDate") LocalDate settlementDate,
                                         @Param("settlementBatchNo") String settlementBatchNo);

    /**
     * 按交易动作分片及 NOT_SETTLED 前置状态同步动作单结算字段。
     * @return 成功更新行数，0 时必须回读区分合法重放与状态冲突
     */
    @Update("""
            UPDATE transaction_operation
            SET settlement_status = 'SETTLED', settlement_currency = #{settlementCurrency},
                settlement_amount = #{settlementAmount}, settlement_rate = #{settlementRate},
                settlement_date = #{settlementDate}, settlement_batch_no = #{settlementBatchNo},
                version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'NOT_SETTLED' AND deleted = 0
            """)
    int markOperationSettled(@Param("transactionId") String transactionId,
                             @Param("transactionDateTime") LocalDateTime transactionDateTime,
                             @Param("settlementCurrency") String settlementCurrency,
                             @Param("settlementAmount") BigDecimal settlementAmount,
                             @Param("settlementRate") BigDecimal settlementRate,
                             @Param("settlementDate") LocalDate settlementDate,
                             @Param("settlementBatchNo") String settlementBatchNo,
                             @Param("now") LocalDateTime now);

    /**
     * 校验交易动作单是否已具有完全一致的结算投影。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_operation
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{settlementBatchNo}
              AND deleted = 0
            """)
    int countMatchingSettledOperation(@Param("transactionId") String transactionId,
                                      @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                      @Param("settlementCurrency") String settlementCurrency,
                                      @Param("settlementAmount") BigDecimal settlementAmount,
                                      @Param("settlementRate") BigDecimal settlementRate,
                                      @Param("settlementDate") LocalDate settlementDate,
                                      @Param("settlementBatchNo") String settlementBatchNo);

    /**
     * 按根交易分片更新主单，并仅允许较新的动作时间/交易号覆盖生命周期结算展示。
     * @return 成功更新行数，0 时需判断相同重放或已有更新动作
     */
    @Update("""
            UPDATE transaction_order
            SET settlement_status = 'SETTLED', settlement_currency = #{settlementCurrency},
                settlement_amount = #{settlementAmount}, settlement_rate = #{settlementRate},
                settlement_date = #{settlementDate}, settlement_batch_no = #{settlementBatchNo},
                settlement_transaction_id = #{transactionId},
                settlement_transaction_date_time = #{transactionDateTime},
                version = version + 1, update_time = #{now}
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND deleted = 0
              AND (settlement_transaction_date_time IS NULL
                   OR settlement_transaction_date_time < #{transactionDateTime}
                   OR (settlement_transaction_date_time = #{transactionDateTime}
                       AND settlement_transaction_id < #{transactionId})
                   OR (settlement_transaction_date_time = #{transactionDateTime}
                       AND settlement_transaction_id = #{transactionId}
                       AND settlement_status = 'NOT_SETTLED'))
            """)
    int markOrderSettled(@Param("operationId") String operationId,
                         @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                         @Param("transactionId") String transactionId,
                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                         @Param("settlementCurrency") String settlementCurrency,
                         @Param("settlementAmount") BigDecimal settlementAmount,
                         @Param("settlementRate") BigDecimal settlementRate,
                         @Param("settlementDate") LocalDate settlementDate,
                         @Param("settlementBatchNo") String settlementBatchNo,
                         @Param("now") LocalDateTime now);

    /**
     * 校验交易主单是否指向当前动作且结算字段完全一致。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_order
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND settlement_transaction_id = #{transactionId}
              AND settlement_transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{settlementBatchNo}
              AND deleted = 0
            """)
    int countMatchingSettledOrder(@Param("operationId") String operationId,
                                  @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                                  @Param("transactionId") String transactionId,
                                  @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                  @Param("settlementCurrency") String settlementCurrency,
                                  @Param("settlementAmount") BigDecimal settlementAmount,
                                  @Param("settlementRate") BigDecimal settlementRate,
                                  @Param("settlementDate") LocalDate settlementDate,
                                  @Param("settlementBatchNo") String settlementBatchNo);

    /**
     * 判断主单是否已由时间或交易号更大的后续动作更新，防止旧任务回退展示状态。
     * @return 更新动作计数，大于 0 时当前旧任务无需覆盖主单
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_order
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND (settlement_transaction_date_time > #{transactionDateTime}
                   OR (settlement_transaction_date_time = #{transactionDateTime}
                       AND settlement_transaction_id > #{transactionId}))
              AND deleted = 0
            """)
    int countNewerOrderSettlement(@Param("operationId") String operationId,
                                  @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                                  @Param("transactionId") String transactionId,
                                  @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 仅在原批次结算字段完全匹配时，将指定清分修订财务状态投影为 REVERSED。
     * @return 成功更新行数，0 时必须回读区分重放与事实冲突
     */
    @Update("""
            UPDATE transaction_finance_state
            SET settlement_status = 'REVERSED', settlement_batch_no = #{reversalBatchNo},
                version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision} AND settlement_status = 'SETTLED'
              AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate}
              AND settlement_batch_no = #{originalBatchNo} AND deleted = 0
            """)
    int markFinanceStateReversed(@Param("transactionId") String transactionId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("clearingRevision") int clearingRevision,
                                 @Param("settlementCurrency") String settlementCurrency,
                                 @Param("settlementAmount") BigDecimal settlementAmount,
                                 @Param("settlementRate") BigDecimal settlementRate,
                                 @Param("settlementDate") LocalDate settlementDate,
                                 @Param("originalBatchNo") String originalBatchNo,
                                 @Param("reversalBatchNo") String reversalBatchNo,
                                 @Param("now") LocalDateTime now);

    /**
     * 校验财务状态是否已由目标冲正批次以原冻结金额完成冲正。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_finance_state
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision} AND settlement_status = 'REVERSED'
              AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{reversalBatchNo}
              AND deleted = 0
            """)
    int countMatchingReversedFinanceState(@Param("transactionId") String transactionId,
                                          @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                          @Param("clearingRevision") int clearingRevision,
                                          @Param("settlementCurrency") String settlementCurrency,
                                          @Param("settlementAmount") BigDecimal settlementAmount,
                                          @Param("settlementRate") BigDecimal settlementRate,
                                          @Param("settlementDate") LocalDate settlementDate,
                                          @Param("reversalBatchNo") String reversalBatchNo);

    /**
     * 仅在原结算字段完全匹配时，将交易动作单投影为 REVERSED。
     * @return 成功更新行数，0 时必须回读区分重放与事实冲突
     */
    @Update("""
            UPDATE transaction_operation
            SET settlement_status = 'REVERSED', settlement_batch_no = #{reversalBatchNo},
                version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{originalBatchNo}
              AND deleted = 0
            """)
    int markOperationReversed(@Param("transactionId") String transactionId,
                              @Param("transactionDateTime") LocalDateTime transactionDateTime,
                              @Param("settlementCurrency") String settlementCurrency,
                              @Param("settlementAmount") BigDecimal settlementAmount,
                              @Param("settlementRate") BigDecimal settlementRate,
                              @Param("settlementDate") LocalDate settlementDate,
                              @Param("originalBatchNo") String originalBatchNo,
                              @Param("reversalBatchNo") String reversalBatchNo,
                              @Param("now") LocalDateTime now);

    /**
     * 校验交易动作单是否已由目标冲正批次完成一致冲正。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_operation
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'REVERSED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{reversalBatchNo}
              AND deleted = 0
            """)
    int countMatchingReversedOperation(@Param("transactionId") String transactionId,
                                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                       @Param("settlementCurrency") String settlementCurrency,
                                       @Param("settlementAmount") BigDecimal settlementAmount,
                                       @Param("settlementRate") BigDecimal settlementRate,
                                       @Param("settlementDate") LocalDate settlementDate,
                                       @Param("reversalBatchNo") String reversalBatchNo);

    /**
     * 仅当主单仍指向被冲正动作且原结算字段一致时投影为 REVERSED。
     * @return 成功更新行数，0 时必须回读区分重放、后续动作或事实冲突
     */
    @Update("""
            UPDATE transaction_order
            SET settlement_status = 'REVERSED', settlement_batch_no = #{reversalBatchNo},
                version = version + 1, update_time = #{now}
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND settlement_transaction_id = #{transactionId}
              AND settlement_transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{originalBatchNo}
              AND deleted = 0
            """)
    int markOrderReversed(@Param("operationId") String operationId,
                          @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                          @Param("transactionId") String transactionId,
                          @Param("transactionDateTime") LocalDateTime transactionDateTime,
                          @Param("settlementCurrency") String settlementCurrency,
                          @Param("settlementAmount") BigDecimal settlementAmount,
                          @Param("settlementRate") BigDecimal settlementRate,
                          @Param("settlementDate") LocalDate settlementDate,
                          @Param("originalBatchNo") String originalBatchNo,
                          @Param("reversalBatchNo") String reversalBatchNo,
                          @Param("now") LocalDateTime now);

    /**
     * 校验主单是否仍指向当前动作并已由目标冲正批次完成一致冲正。
     * @return 匹配行数，1 表示合法幂等重放
     */
    @Select("""
            SELECT COUNT(1) FROM transaction_order
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND settlement_transaction_id = #{transactionId}
              AND settlement_transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'REVERSED' AND settlement_currency = #{settlementCurrency}
              AND settlement_amount = #{settlementAmount} AND settlement_rate = #{settlementRate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{reversalBatchNo}
              AND deleted = 0
            """)
    int countMatchingReversedOrder(@Param("operationId") String operationId,
                                   @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                                   @Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("settlementCurrency") String settlementCurrency,
                                   @Param("settlementAmount") BigDecimal settlementAmount,
                                   @Param("settlementRate") BigDecimal settlementRate,
                                   @Param("settlementDate") LocalDate settlementDate,
                                   @Param("reversalBatchNo") String reversalBatchNo);

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
