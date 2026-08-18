package com.scott.payment.component.db.cache.mapper;

import com.scott.payment.component.db.cache.entity.ManagedCacheInvalidationOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ManagedCacheInvalidationOutboxMapper
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 共享永久缓存失效 Outbox 数据访问接口，提供事务内写入、到期扫描和 CAS 状态迁移
 * @status : create
 */
public interface ManagedCacheInvalidationOutboxMapper {

    /**
     * 在业务事务内持久化缓存失效意图。
     *
     * @param event 初始状态为 INIT、版本为 0 的事件
     * @return 写入行数，成功时必须为 1
     */
    @Insert("""
            INSERT INTO merchant_security_cache_invalidation_outbox (
                event_id, cache_name, business_key, gate_token, event_status,
                retry_count, next_retry_time, published_time,
                failure_reason, version, create_time, update_time
            ) VALUES (
                #{eventId}, #{cacheName}, #{businessKey}, #{gateToken}, #{eventStatus},
                #{retryCount}, #{nextRetryTime}, #{publishedTime},
                #{failureReason}, #{version}, #{createTime}, #{updateTime}
            )
            """)
    int insertEvent(ManagedCacheInvalidationOutboxDO event);

    /**
     * 按事件唯一标识查询当前状态。
     *
     * @param eventId 事件唯一标识
     * @return Outbox 记录；不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM merchant_security_cache_invalidation_outbox
            WHERE event_id = #{eventId}
            """)
    ManagedCacheInvalidationOutboxDO selectByEventId(@Param("eventId") String eventId);

    /**
     * 按稳定顺序查询到期的 INIT/FAILED 事件。
     *
     * @param now 当前补偿时间
     * @param limit 单批最大事件数
     * @return 当前允许重试的事件
     */
    @Select("""
            SELECT *
            FROM merchant_security_cache_invalidation_outbox
            WHERE event_status IN ('INIT', 'FAILED')
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<ManagedCacheInvalidationOutboxDO> selectDueEvents(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * 使用版本号 CAS 将事件推进到 SENT 终态。
     *
     * @param id Outbox 主键
     * @param version 调用方读取到的版本
     * @param publishedTime 缓存删除成功时间
     * @return 更新行数；0 表示并发方已经处理
     */
    @Update("""
            UPDATE merchant_security_cache_invalidation_outbox
            SET event_status = 'SENT',
                published_time = #{publishedTime},
                next_retry_time = NULL,
                failure_reason = NULL,
                version = version + 1,
                update_time = #{publishedTime}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status IN ('INIT', 'FAILED')
            """)
    int markSent(@Param("id") Long id,
                 @Param("version") Integer version,
                 @Param("publishedTime") LocalDateTime publishedTime);

    /**
     * 使用版本号 CAS 记录发布失败并安排下次重试。
     *
     * @param id Outbox 主键
     * @param version 调用方读取到的版本
     * @param nextRetryTime 下一次允许补偿的时间
     * @param failureReason 长度受控且不含敏感数据的原因
     * @param now 本次失败处理时间
     * @return 更新行数；0 表示状态或版本已经变化
     */
    @Update("""
            UPDATE merchant_security_cache_invalidation_outbox
            SET event_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                failure_reason = #{failureReason},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status IN ('INIT', 'FAILED')
            """)
    int markFailed(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("failureReason") String failureReason,
                   @Param("now") LocalDateTime now);
}
