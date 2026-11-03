package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 结算事件 Outbox 数据访问接口；通过事件唯一键、短租约和 version CAS 支撑 MQ 至少一次发送，不假设网络发送与成功标记原子。
 */
public interface SettlementEventOutboxMapper {

    /**
     * 在结算结果事务内幂等追加一条跨币种汇率使用事件。
     *
     * <p>candidate_id 对批次汇率事件为空，rate_usage_id 使用 settlement_batch_rate.id；
     * 唯一键冲突保留原行，服务层必须随后按 event_no 回读并核对冻结事实。</p>
     *
     * @param row 已冻结的普通消息 Outbox
     * @return 新增或唯一键命中的数据库影响行数
     */
    @Insert("""
            INSERT INTO settlement_event_outbox
            (event_no, settlement_batch_no, candidate_id, rate_usage_id, topic, tag, message_key,
             message_group, payload_json, event_status, retry_count, next_retry_time,
             version, create_time, update_time)
            VALUES
            (#{row.eventNo}, #{row.settlementBatchNo}, NULL, #{row.rateUsageId}, #{row.topic},
             #{row.tag}, #{row.messageKey}, #{row.messageGroup}, #{row.payloadJson},
             #{row.eventStatus}, #{row.retryCount}, #{row.nextRetryTime}, #{row.version},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertRateUsageIdempotent(@Param("row") SettlementEventOutboxDO row);

    /**
     * 按全局事件号回读冻结 Outbox，用于幂等冲突后的完整身份核对。
     *
     * @param eventNo 全局稳定事件号
     * @return 已提交 Outbox；不存在时返回空
     */
    @Select("""
            SELECT *
            FROM settlement_event_outbox
            WHERE event_no = #{eventNo}
            LIMIT 1
            """)
    SettlementEventOutboxDO selectByEventNo(@Param("eventNo") String eventNo);

    /**
     * 锁定一条到期或 PROCESSING 超时事件。
     */
    @Select("""
            SELECT *
            FROM settlement_event_outbox
            WHERE (event_status IN ('INIT', 'FAILED') AND next_retry_time <= #{now})
               OR (event_status = 'PROCESSING' AND processing_deadline <= #{now})
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    SettlementEventOutboxDO selectNextDueForUpdate(@Param("now") LocalDateTime now);

    /**
     * 领取有限期发送租约，崩溃后可由其它实例恢复。
     */
    @Update("""
            UPDATE settlement_event_outbox
            SET event_status = 'PROCESSING',
                processing_owner = #{owner},
                processing_deadline = #{deadline},
                version = version + 1,
                update_time = #{now}
            WHERE event_no = #{eventNo}
              AND ((event_status IN ('INIT', 'FAILED') AND next_retry_time <= #{now})
                   OR (event_status = 'PROCESSING' AND processing_deadline <= #{now}))
              AND version = #{expectedVersion}
            """)
    int claim(@Param("eventNo") String eventNo,
              @Param("owner") String owner,
              @Param("deadline") LocalDateTime deadline,
              @Param("expectedVersion") long expectedVersion,
              @Param("now") LocalDateTime now);

    /**
     * 发送成功后以租约所有者和版本 CAS 标记 SENT。
     */
    @Update("""
            UPDATE settlement_event_outbox
            SET event_status = 'SENT',
                sent_time = #{now},
                processing_owner = NULL,
                processing_deadline = NULL,
                last_failure_code = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE event_no = #{eventNo}
              AND event_status = 'PROCESSING'
              AND processing_owner = #{owner}
              AND version = #{expectedVersion}
            """)
    int markSent(@Param("eventNo") String eventNo,
                 @Param("owner") String owner,
                 @Param("expectedVersion") long expectedVersion,
                 @Param("now") LocalDateTime now);

    /**
     * 发送失败释放租约并写入服务层计算的有上限指数退避时间。
     */
    @Update("""
            UPDATE settlement_event_outbox
            SET event_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                processing_owner = NULL,
                processing_deadline = NULL,
                last_failure_code = #{failureCode},
                version = version + 1,
                update_time = #{now}
            WHERE event_no = #{eventNo}
              AND event_status = 'PROCESSING'
              AND processing_owner = #{owner}
              AND version = #{expectedVersion}
            """)
    int markFailed(@Param("eventNo") String eventNo,
                   @Param("owner") String owner,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("failureCode") String failureCode,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);
}
