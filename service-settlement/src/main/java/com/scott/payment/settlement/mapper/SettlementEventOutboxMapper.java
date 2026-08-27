package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 结算事件 Outbox 状态 Mapper；MQ 发送成功与数据库标记之间按至少一次语义处理。 */
public interface SettlementEventOutboxMapper {

    /** 锁定一条到期或 PROCESSING 超时事件。 */
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

    /** 领取有限期发送租约，崩溃后可由其它实例恢复。 */
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

    /** 发送成功后以租约所有者和版本 CAS 标记 SENT。 */
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

    /** 发送失败释放租约并写入服务层计算的有上限指数退避时间。 */
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
