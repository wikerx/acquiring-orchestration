package com.scott.payment.component.db.outbox.mapper;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.model.ReliableMqOutboxMetricsSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxMapper
 * @date : 2026-08-02 22:10
 * @email : scott_x@163.com
 * @description : 非交易可靠 MQ Outbox 数据访问接口，只执行参数化 Insert、查询和状态 CAS
 * @status : create
 */
public interface ReliableMqOutboxMapper {

    /** 在调用方事务中写入消息意图。 */
    @Insert("""
            INSERT INTO sys_mq_outbox (
                event_id, topic, tag, producer_service, trace_id, payload_json,
                event_status, retry_count, max_retry_count, next_retry_time,
                processing_started_time, sent_time, failure_reason, version,
                create_time, update_time
            ) VALUES (
                #{eventId}, #{topic}, #{tag}, #{producerService}, #{traceId}, #{payloadJson},
                #{eventStatus}, #{retryCount}, #{maxRetryCount}, #{nextRetryTime},
                #{processingStartedTime}, #{sentTime}, #{failureReason}, #{version},
                #{createTime}, #{updateTime}
            )
            """)
    int insert(ReliableMqOutboxDO event);

    /** 按唯一事件号查询消息。 */
    @Select("SELECT * FROM sys_mq_outbox WHERE event_id = #{eventId}")
    ReliableMqOutboxDO selectByEventId(@Param("eventId") String eventId);

    /** 查询已到投递时间的消息。 */
    @Select("""
            SELECT *
            FROM sys_mq_outbox
            WHERE event_status IN ('INIT', 'RETRY_WAIT')
              AND retry_count < max_retry_count
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<ReliableMqOutboxDO> selectDue(@Param("now") LocalDateTime now,
                                      @Param("limit") int limit);

    /** CAS 抢占一条待投递消息。 */
    @Update("""
            UPDATE sys_mq_outbox
            SET event_status = 'PROCESSING',
                processing_started_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status IN ('INIT', 'RETRY_WAIT')
              AND retry_count < max_retry_count
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            """)
    int claim(@Param("id") Long id,
              @Param("version") Integer version,
              @Param("now") LocalDateTime now);

    /** CAS 标记投递成功。 */
    @Update("""
            UPDATE sys_mq_outbox
            SET event_status = 'SENT',
                sent_time = #{now},
                next_retry_time = NULL,
                processing_started_time = NULL,
                failure_reason = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status = 'PROCESSING'
            """)
    int markSent(@Param("id") Long id,
                 @Param("version") Integer version,
                 @Param("now") LocalDateTime now);

    /** CAS 记录失败、重试等待或关闭状态。 */
    @Update("""
            UPDATE sys_mq_outbox
            SET event_status = #{targetStatus},
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                processing_started_time = NULL,
                failure_reason = #{failureReason},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status = 'PROCESSING'
            """)
    int markFailed(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("targetStatus") String targetStatus,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("failureReason") String failureReason,
                   @Param("now") LocalDateTime now);

    /** 恢复投递进程崩溃后遗留的 PROCESSING 消息。 */
    @Update("""
            UPDATE sys_mq_outbox
            SET event_status = CASE
                    WHEN retry_count + 1 >= max_retry_count THEN 'CLOSED'
                    ELSE 'RETRY_WAIT'
                END,
                retry_count = retry_count + 1,
                next_retry_time = CASE
                    WHEN retry_count + 1 >= max_retry_count THEN NULL
                    ELSE #{now}
                END,
                processing_started_time = NULL,
                failure_reason = 'relay processing timeout',
                version = version + 1,
                update_time = #{now}
            WHERE event_status = 'PROCESSING'
              AND processing_started_time < #{staleBefore}
            """)
    int recoverStale(@Param("staleBefore") LocalDateTime staleBefore,
                     @Param("now") LocalDateTime now);

    /** 查询低基数 Outbox 运维指标快照。 */
    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN event_status = 'INIT' THEN 1 ELSE 0 END), 0) AS init_count,
              COALESCE(SUM(CASE WHEN event_status = 'PROCESSING' THEN 1 ELSE 0 END), 0) AS processing_count,
              COALESCE(SUM(CASE WHEN event_status = 'RETRY_WAIT' THEN 1 ELSE 0 END), 0) AS retry_wait_count,
              COALESCE(SUM(CASE WHEN event_status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_count,
              MIN(CASE WHEN event_status IN ('INIT', 'PROCESSING', 'RETRY_WAIT')
                       THEN create_time ELSE NULL END) AS oldest_pending_time
            FROM sys_mq_outbox
            """)
    ReliableMqOutboxMetricsSnapshot selectMetricsSnapshot();

    /** 使用事件号和版本 CAS 将一条 CLOSED 消息恢复为待投递状态。 */
    @Update("""
            UPDATE sys_mq_outbox
            SET event_status = 'RETRY_WAIT',
                retry_count = 0,
                next_retry_time = #{now},
                processing_started_time = NULL,
                sent_time = NULL,
                failure_reason = #{recoveryReason},
                version = version + 1,
                update_time = #{now}
            WHERE event_id = #{eventId}
              AND version = #{expectedVersion}
              AND event_status = 'CLOSED'
            """)
    int recoverClosed(@Param("eventId") String eventId,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("recoveryReason") String recoveryReason,
                      @Param("now") LocalDateTime now);
}
