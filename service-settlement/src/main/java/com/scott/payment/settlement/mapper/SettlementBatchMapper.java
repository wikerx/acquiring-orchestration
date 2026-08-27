package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchMapper
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次 Mapper，提供创建幂等锁读和候选计数 CAS，不承载汇率计算或资金规则。
 * @status : create
 */
public interface SettlementBatchMapper {

    /**
     * 锁定下一条已封批或可恢复批次；活跃租约不会进入结果，过期租约可由其它实例接管。
     */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE candidate_count > 0
              AND batch_status IN ('CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
            ORDER BY business_date ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    SettlementBatchDO selectNextProcessableForUpdate(@Param("now") java.time.LocalDateTime now);

    /** 使用处理状态、租约空闲条件和原 version CAS 获取有限期租约。 */
    @Update("""
            UPDATE settlement_batch
            SET processing_owner = #{owner},
                processing_deadline = #{deadline},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND candidate_count > 0
              AND batch_status IN ('CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
              AND (processing_deadline IS NULL OR processing_deadline <= #{now}
                   OR (processing_owner = #{owner} AND processing_deadline > #{now}))
              AND version = #{expectedVersion}
            """)
    int acquireProcessingLease(@Param("settlementBatchNo") String settlementBatchNo,
                               @Param("owner") String owner,
                               @Param("now") java.time.LocalDateTime now,
                               @Param("deadline") java.time.LocalDateTime deadline,
                               @Param("expectedVersion") long expectedVersion);

    /** 当前所有者只能在租约未过期时续租，防止失去所有权的旧实例复活覆盖。 */
    @Update("""
            UPDATE settlement_batch
            SET processing_deadline = #{deadline},
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND processing_owner = #{owner}
              AND processing_deadline > #{now}
              AND #{deadline} > #{now}
              AND batch_status IN ('CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
            """)
    int renewProcessingLease(@Param("settlementBatchNo") String settlementBatchNo,
                             @Param("owner") String owner,
                             @Param("now") java.time.LocalDateTime now,
                             @Param("deadline") java.time.LocalDateTime deadline);

    /** 按批次号、请求键和业务日序号唯一约束幂等写入，随后必须回读并校验完整身份。 */
    @Insert("""
            INSERT INTO settlement_batch
            (settlement_batch_no, create_request_key, business_date, business_time_zone,
             daily_sequence, merchant_id, settlement_profile_id, settlement_account_id,
             target_currency, target_currency_exponent, batch_type, original_batch_no,
             cutoff_begin_time, cutoff_end_time, batch_status, candidate_count, retry_count,
             version, create_time, update_time)
            VALUES
            (#{row.settlementBatchNo}, #{row.createRequestKey}, #{row.businessDate}, #{row.businessTimeZone},
             #{row.dailySequence}, #{row.merchantId}, #{row.settlementProfileId}, #{row.settlementAccountId},
             #{row.targetCurrency}, #{row.targetCurrencyExponent}, #{row.batchType}, #{row.originalBatchNo},
             #{row.cutoffBeginTime}, #{row.cutoffEndTime}, #{row.batchStatus}, #{row.candidateCount},
             #{row.retryCount}, #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementBatchDO row);

    /** 锁定并读取 create_request_key 的既有幂等结果。 */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE create_request_key = #{createRequestKey}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementBatchDO selectByCreateRequestKeyForUpdate(@Param("createRequestKey") String createRequestKey);

    /** 按批次号锁定批次，作为候选认领固定首锁。 */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE settlement_batch_no = #{settlementBatchNo}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementBatchDO selectByBatchNoForUpdate(@Param("settlementBatchNo") String settlementBatchNo);

    /** 锁定已存在的原批次冲正，支持人工命令幂等重放。 */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE original_batch_no = #{originalBatchNo}
              AND batch_type = 'REVERSAL'
            ORDER BY id ASC
            LIMIT 1
            FOR UPDATE
            """)
    SettlementBatchDO selectReversalByOriginalForUpdate(
            @Param("originalBatchNo") String originalBatchNo);

    /** 候选与审计关系写入后，使用批次状态和版本 CAS 增加计数并进入 CLAIMING。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CLAIMING',
                candidate_count = candidate_count + 1,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CREATED', 'CLAIMING')
              AND version = #{expectedVersion}
            """)
    int incrementCandidateCount(@Param("settlementBatchNo") String settlementBatchNo,
                                @Param("expectedVersion") long expectedVersion);

    /** 自动批量认领后按实际页大小增加候选数，状态与 version 共同防止跨实例覆盖。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CLAIMING',
                candidate_count = candidate_count + #{delta},
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CREATED', 'CLAIMING')
              AND #{delta} > 0
              AND version = #{expectedVersion}
            """)
    int incrementCandidateCountBy(@Param("settlementBatchNo") String settlementBatchNo,
                                  @Param("delta") int delta,
                                  @Param("expectedVersion") long expectedVersion);

    /** 候选扫描出现空页后封闭非空批次，后续汇率处理只接受 CLAIMED。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CLAIMED',
                version = version + 1,
                update_time = #{sealedTime}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CREATED', 'CLAIMING')
              AND candidate_count = #{expectedCandidateCount}
              AND candidate_count > 0
              AND version = #{expectedVersion}
            """)
    int sealClaimedBatch(@Param("settlementBatchNo") String settlementBatchNo,
                         @Param("expectedCandidateCount") int expectedCandidateCount,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("sealedTime") java.time.LocalDateTime sealedTime);

    /** 完整汇率矩阵回读验证后，以租约、当前状态和 version CAS 进入 RATE_LOCKED。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'RATE_LOCKED',
                rate_locked_time = #{lockedTime},
                last_failure_stage = NULL,
                last_failure_code = NULL,
                last_failure_message = NULL,
                version = version + 1,
                update_time = #{lockedTime}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CLAIMED', 'FAILED_RETRYABLE')
              AND processing_owner = #{owner}
              AND processing_deadline > #{lockedTime}
              AND version = #{expectedVersion}
            """)
    int markRateLocked(@Param("settlementBatchNo") String settlementBatchNo,
                       @Param("owner") String owner,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("lockedTime") java.time.LocalDateTime lockedTime);

    /** 结果事务开始时以租约和 version CAS 进入 CALCULATING。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CALCULATING',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status = 'RATE_LOCKED'
              AND processing_owner = #{owner}
              AND processing_deadline > #{now}
              AND version = #{expectedVersion}
            """)
    int beginCalculating(@Param("settlementBatchNo") String settlementBatchNo,
                         @Param("owner") String owner,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("now") java.time.LocalDateTime now);

    /** 结果和汇总回读一致后结束计算租约并进入 CALCULATED，后续由独立资金事务竞争 POSTING。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CALCULATED',
                calculated_time = #{calculatedTime},
                processing_owner = NULL,
                processing_deadline = NULL,
                last_failure_stage = NULL,
                last_failure_code = NULL,
                last_failure_message = NULL,
                version = version + 1,
                update_time = #{calculatedTime}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status = 'CALCULATING'
              AND processing_owner = #{owner}
              AND processing_deadline > #{calculatedTime}
              AND version = #{expectedVersion}
            """)
    int markCalculated(@Param("settlementBatchNo") String settlementBatchNo,
                       @Param("owner") String owner,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("calculatedTime") java.time.LocalDateTime calculatedTime);

    /** 资金事务开始前以租约和 version CAS 进入 POSTING。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'POSTING',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND (batch_status = 'CALCULATED'
                   OR (batch_status = 'FAILED_RETRYABLE' AND last_failure_stage = 'LEDGER_POSTING'))
              AND processing_owner = #{owner}
              AND processing_deadline > #{now}
              AND version = #{expectedVersion}
            """)
    int beginPosting(@Param("settlementBatchNo") String settlementBatchNo,
                     @Param("owner") String owner,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("now") java.time.LocalDateTime now);

    /** 资金、保证金、候选及投影任务全部落库后结束租约并提交 POSTED。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'POSTED',
                posted_time = #{postedTime},
                processing_owner = NULL,
                processing_deadline = NULL,
                last_failure_stage = NULL,
                last_failure_code = NULL,
                last_failure_message = NULL,
                version = version + 1,
                update_time = #{postedTime}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status = 'POSTING'
              AND processing_owner = #{owner}
              AND processing_deadline > #{postedTime}
              AND version = #{expectedVersion}
            """)
    int markPosted(@Param("settlementBatchNo") String settlementBatchNo,
                   @Param("owner") String owner,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("postedTime") java.time.LocalDateTime postedTime);

    /** 入账前取消批次；有活跃处理租约时由服务层拒绝。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'CANCELLED',
                cancelled_time = #{now},
                processing_owner = NULL,
                processing_deadline = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED',
                                   'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
              AND version = #{expectedVersion}
            """)
    int cancelBeforePosting(@Param("settlementBatchNo") String settlementBatchNo,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("now") java.time.LocalDateTime now);

    /** 原批次由 POSTED 进入 REVERSING，禁止并发创建多条冲正链。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'REVERSING',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status = 'POSTED'
              AND version = #{expectedVersion}
            """)
    int markReversing(@Param("settlementBatchNo") String settlementBatchNo,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("now") java.time.LocalDateTime now);

    /** 新冲正批次直接进入本地资金事务的 POSTING 状态并冻结原候选数。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'POSTING',
                candidate_count = #{candidateCount},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_type = 'REVERSAL'
              AND batch_status = 'CREATED'
              AND candidate_count = 0
              AND version = #{expectedVersion}
            """)
    int prepareReversalPosting(@Param("settlementBatchNo") String settlementBatchNo,
                               @Param("candidateCount") int candidateCount,
                               @Param("expectedVersion") long expectedVersion,
                               @Param("now") java.time.LocalDateTime now);

    /** 冲正资金和审计落库后同时完成新批并关闭原批冲正状态。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'POSTED',
                posted_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_type = 'REVERSAL'
              AND batch_status = 'POSTING'
              AND version = #{expectedVersion}
            """)
    int markReversalPosted(@Param("settlementBatchNo") String settlementBatchNo,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("now") java.time.LocalDateTime now);

    /** 原批次只允许从 REVERSING 进入 REVERSED。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'REVERSED',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status = 'REVERSING'
              AND version = #{expectedVersion}
            """)
    int markReversed(@Param("settlementBatchNo") String settlementBatchNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("now") java.time.LocalDateTime now);

    /** 可重试失败释放所有者并把 processing_deadline 改作下次数据库扫描时间。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'FAILED_RETRYABLE',
                retry_count = retry_count + 1,
                processing_owner = NULL,
                processing_deadline = #{nextRetryTime},
                last_failure_stage = #{failureStage},
                last_failure_code = #{failureCode},
                last_failure_message = #{failureMessage},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED', 'POSTING', 'FAILED_RETRYABLE')
              AND processing_owner = #{owner}
              AND processing_deadline > #{now}
              AND retry_count = #{expectedRetryCount}
              AND version = #{expectedVersion}
            """)
    int recordRetryableFailure(@Param("settlementBatchNo") String settlementBatchNo,
                               @Param("owner") String owner,
                               @Param("expectedRetryCount") int expectedRetryCount,
                               @Param("expectedVersion") long expectedVersion,
                               @Param("failureStage") String failureStage,
                               @Param("failureCode") String failureCode,
                               @Param("failureMessage") String failureMessage,
                               @Param("now") java.time.LocalDateTime now,
                               @Param("nextRetryTime") java.time.LocalDateTime nextRetryTime);

    /** 稳定数据冲突或重试耗尽后释放租约并进入人工复核。 */
    @Update("""
            UPDATE settlement_batch
            SET batch_status = 'MANUAL_REVIEW',
                processing_owner = NULL,
                processing_deadline = NULL,
                last_failure_stage = #{failureStage},
                last_failure_code = #{failureCode},
                last_failure_message = #{failureMessage},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND batch_status IN ('CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED', 'POSTING', 'FAILED_RETRYABLE')
              AND processing_owner = #{owner}
              AND processing_deadline > #{now}
              AND retry_count = #{expectedRetryCount}
              AND version = #{expectedVersion}
            """)
    int recordManualReview(@Param("settlementBatchNo") String settlementBatchNo,
                           @Param("owner") String owner,
                           @Param("expectedRetryCount") int expectedRetryCount,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("failureStage") String failureStage,
                           @Param("failureCode") String failureCode,
                           @Param("failureMessage") String failureMessage,
                           @Param("now") java.time.LocalDateTime now);
}
