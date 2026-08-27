package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemFactsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTierPeriodReplayMapper
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 阶梯期间重放控制和稳定动作项 Mapper；所有状态变更均带当前状态与版本 CAS。
 * @status : create
 */
public interface ClearingTierPeriodReplayMapper {

    /** 以 request_key 数据库唯一键吸收重复申请。 */
    @Insert("""
            INSERT INTO clearing_tier_period_replay
            (replay_no, request_key, merchant_id, fee_plan_id, fee_plan_version_id,
             trigger_fee_rule_id, period_key, period_start, period_end, reason,
             submit_operator, replay_status, item_count, completed_count, version,
             create_time, update_time)
            VALUES
            (#{row.replayNo}, #{row.requestKey}, #{row.merchantId}, #{row.feePlanId},
             #{row.feePlanVersionId}, #{row.triggerFeeRuleId}, #{row.periodKey},
             #{row.periodStart}, #{row.periodEnd}, #{row.reason}, #{row.submitOperator},
             #{row.replayStatus}, 0, 0, 0, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") ClearingTierPeriodReplayDO row);

    @Select("""
            SELECT * FROM clearing_tier_period_replay
            WHERE request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    ClearingTierPeriodReplayDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    @Select("""
            SELECT * FROM clearing_tier_period_replay
            WHERE replay_no = #{replayNo}
            LIMIT 1 FOR UPDATE
            """)
    ClearingTierPeriodReplayDO selectForUpdate(@Param("replayNo") String replayNo);

    /** 事务外读取申请身份，用于加载不可变费用版本；复核事务仍会重新 FOR UPDATE。 */
    @Select("""
            SELECT * FROM clearing_tier_period_replay
            WHERE replay_no = #{replayNo}
            LIMIT 1
            """)
    ClearingTierPeriodReplayDO selectByReplayNo(@Param("replayNo") String replayNo);

    /** 先公开 PREPARING 门禁，再等待阶梯累计行锁，阻断新交易进入相同累计维度。 */
    @Update("""
            UPDATE clearing_tier_period_replay
            SET replay_status = 'PREPARING', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markPreparing(@Param("replayNo") String replayNo,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("reviewOperator") String reviewOperator,
                      @Param("reviewComment") String reviewComment,
                      @Param("now") LocalDateTime now);

    /**
     * 在累计行已锁定后冻结同商户、同不可变费用版本、同月份的全部完成动作。
     * 保证金计数与结算状态只用于严格门禁，不能在 Mapper 内转换为业务判断。
     */
    @Select("""
            SELECT fs.finance_state_id, fs.transaction_id, fs.transaction_date_time,
                   fs.clearing_revision, fs.version AS finance_state_version,
                   op.clearing_complete_time,
                   fs.settlement_status
            FROM transaction_finance_state fs
            JOIN transaction_operation op
              ON op.transaction_id = fs.transaction_id
             AND op.operation_id = fs.operation_id
             AND op.transaction_date_time = fs.transaction_date_time
             AND op.deleted = 0
            WHERE fs.merchant_id = #{merchantId}
              AND fs.fee_plan_version_id = #{feePlanVersionId}
              AND fs.clearing_status = 'CLEARED'
              AND fs.transaction_date_time >= #{periodStart}
              AND fs.transaction_date_time < #{periodEnd}
              AND op.clearing_complete_time IS NOT NULL
            ORDER BY op.clearing_complete_time ASC, fs.transaction_id ASC
            FOR UPDATE
            """)
    List<ClearingTierPeriodReplayItemFactsDO> selectPeriodItems(
            @Param("merchantId") String merchantId,
            @Param("feePlanVersionId") Long feePlanVersionId,
            @Param("periodKey") String periodKey,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /** 以同一精确分片时间范围一次统计活动保证金事实，避免按重放项逐笔查询。 */
    @Select("""
            SELECT COUNT(1)
            FROM transaction_finance_state fs
            JOIN transaction_reserve_clearing_detail rd
              ON rd.transaction_id = fs.transaction_id
             AND rd.transaction_date_time = fs.transaction_date_time
             AND rd.clearing_revision = fs.clearing_revision
            WHERE fs.merchant_id = #{merchantId}
              AND fs.fee_plan_version_id = #{feePlanVersionId}
              AND fs.clearing_status = 'CLEARED'
              AND fs.transaction_date_time >= #{periodStart}
              AND fs.transaction_date_time < #{periodEnd}
              AND rd.record_status = 'ACTIVE'
            """)
    long countActiveReserveDetails(@Param("merchantId") String merchantId,
                                   @Param("feePlanVersionId") Long feePlanVersionId,
                                   @Param("periodStart") LocalDateTime periodStart,
                                   @Param("periodEnd") LocalDateTime periodEnd);

    @Insert("""
            <script>
            INSERT INTO clearing_tier_period_replay_item
            (replay_no, sequence_no, finance_state_id, transaction_id, transaction_date_time,
             expected_clearing_revision, expected_finance_state_version, clearing_complete_time,
             item_status, attempt_count, version, create_time, update_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
              (#{row.replayNo}, #{row.sequenceNo}, #{row.financeStateId}, #{row.transactionId},
               #{row.transactionDateTime}, #{row.expectedClearingRevision},
               #{row.expectedFinanceStateVersion}, #{row.clearingCompleteTime},
               #{row.itemStatus}, 0, 0, #{row.createTime}, #{row.updateTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertItems(@Param("rows") List<ClearingTierPeriodReplayItemDO> rows);

    @Update("""
            UPDATE clearing_tier_period_replay
            SET replay_status = 'RUNNING', item_count = #{itemCount}, completed_count = 0,
                last_error_code = NULL, last_error_message = NULL,
                version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'PREPARING'
              AND version = #{expectedVersion}
            """)
    int markRunning(@Param("replayNo") String replayNo,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("itemCount") int itemCount,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE clearing_tier_period_replay
            SET replay_status = 'MANUAL_REVIEW', last_error_code = #{errorCode},
                last_error_message = #{errorMessage}, version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'PREPARING'
              AND version = #{expectedVersion}
            """)
    int markManualReview(@Param("replayNo") String replayNo,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("errorCode") String errorCode,
                         @Param("errorMessage") String errorMessage,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE clearing_tier_period_replay
            SET replay_status = 'REJECTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markRejected(@Param("replayNo") String replayNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("now") LocalDateTime now);

    /** 正常清分进入阶梯累计前检查相同累计闭包是否正处于准备、运行或人工处置。 */
    @Select("""
            SELECT COUNT(1) FROM clearing_tier_period_replay
            WHERE merchant_id = #{merchantId}
              AND fee_plan_version_id = #{feePlanVersionId}
              AND period_key = #{periodKey}
              AND replay_status IN ('PREPARING', 'RUNNING', 'MANUAL_REVIEW')
            """)
    int countBlocking(@Param("merchantId") String merchantId,
                      @Param("feePlanVersionId") Long feePlanVersionId,
                      @Param("periodKey") String periodKey);

    /** 扫描当前存在到期下一项的运行中重放；不在扫描事务内持有行锁。 */
    @Select("""
            SELECT r.* FROM clearing_tier_period_replay r
            WHERE r.replay_status = 'RUNNING'
              AND EXISTS (
                SELECT 1 FROM clearing_tier_period_replay_item i
                WHERE i.replay_no = r.replay_no
                  AND i.sequence_no = r.completed_count + 1
                  AND (i.item_status = 'PENDING'
                       OR (i.item_status = 'FAILED' AND i.next_retry_time <= #{now})))
            ORDER BY r.update_time ASC, r.id ASC
            LIMIT #{limit}
            """)
    List<ClearingTierPeriodReplayDO> selectRunnable(@Param("now") LocalDateTime now,
                                                    @Param("limit") int limit);

    /** 事务外读取稳定下一项，用于加载费用版本和动作上下文；最终提交仍会重新加锁校验。 */
    @Select("""
            SELECT i.* FROM clearing_tier_period_replay_item i
            JOIN clearing_tier_period_replay r ON r.replay_no = i.replay_no
            WHERE i.replay_no = #{replayNo} AND r.replay_status = 'RUNNING'
              AND i.sequence_no = r.completed_count + 1
              AND (i.item_status = 'PENDING'
                   OR (i.item_status = 'FAILED' AND i.next_retry_time <= #{now}))
            LIMIT 1
            """)
    ClearingTierPeriodReplayItemDO selectNextItem(@Param("replayNo") String replayNo,
                                                  @Param("now") LocalDateTime now);

    /** 单项提交事务内锁定唯一下一序号，防止多实例并发跳序。 */
    @Select("""
            SELECT i.* FROM clearing_tier_period_replay_item i
            JOIN clearing_tier_period_replay r ON r.replay_no = i.replay_no
            WHERE i.replay_no = #{replayNo} AND r.replay_status = 'RUNNING'
              AND i.sequence_no = r.completed_count + 1
              AND (i.item_status = 'PENDING'
                   OR (i.item_status = 'FAILED' AND i.next_retry_time <= #{now}))
            LIMIT 1 FOR UPDATE
            """)
    ClearingTierPeriodReplayItemDO selectNextItemForUpdate(@Param("replayNo") String replayNo,
                                                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE clearing_tier_period_replay_item
            SET item_status = 'COMPLETED', processed_revision = #{processedRevision},
                processed_time = #{now}, next_retry_time = NULL,
                last_error_code = NULL, last_error_message = NULL,
                version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND sequence_no = #{sequenceNo}
              AND item_status IN ('PENDING', 'FAILED') AND version = #{expectedVersion}
            """)
    int markItemCompleted(@Param("replayNo") String replayNo,
                          @Param("sequenceNo") int sequenceNo,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("processedRevision") int processedRevision,
                          @Param("now") LocalDateTime now);

    /** 完成项与控制游标同事务推进；最后一项自动关闭重放。 */
    @Update("""
            UPDATE clearing_tier_period_replay
            SET completed_count = completed_count + 1,
                last_clearing_complete_time = #{clearingCompleteTime},
                last_transaction_id = #{transactionId},
                replay_status = CASE WHEN completed_count + 1 = item_count
                                     THEN 'COMPLETED' ELSE 'RUNNING' END,
                completed_time = CASE WHEN completed_count + 1 = item_count
                                      THEN #{now} ELSE NULL END,
                last_error_code = NULL, last_error_message = NULL,
                version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'RUNNING'
              AND completed_count = #{sequenceNo} - 1 AND version = #{expectedVersion}
            """)
    int advanceAfterItem(@Param("replayNo") String replayNo,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("sequenceNo") int sequenceNo,
                         @Param("clearingCompleteTime") LocalDateTime clearingCompleteTime,
                         @Param("transactionId") String transactionId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE clearing_tier_period_replay_item
            SET item_status = 'FAILED', attempt_count = attempt_count + 1,
                next_retry_time = #{nextRetryTime}, last_error_code = #{errorCode},
                last_error_message = #{errorMessage}, version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND sequence_no = #{sequenceNo}
              AND item_status IN ('PENDING', 'FAILED') AND version = #{expectedVersion}
            """)
    int markItemFailed(@Param("replayNo") String replayNo,
                       @Param("sequenceNo") int sequenceNo,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("nextRetryTime") LocalDateTime nextRetryTime,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE clearing_tier_period_replay
            SET replay_status = #{targetStatus}, last_error_code = #{errorCode},
                last_error_message = #{errorMessage}, version = version + 1, update_time = #{now}
            WHERE replay_no = #{replayNo} AND replay_status = 'RUNNING'
              AND version = #{expectedVersion}
              AND #{targetStatus} IN ('RUNNING', 'MANUAL_REVIEW')
            """)
    int recordItemFailure(@Param("replayNo") String replayNo,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("targetStatus") String targetStatus,
                          @Param("errorCode") String errorCode,
                          @Param("errorMessage") String errorMessage,
                          @Param("now") LocalDateTime now);
}
