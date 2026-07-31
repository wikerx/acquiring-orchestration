package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.MerchantSecurityCacheInvalidationOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationOutboxMapper
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 管理端数据访问层永久缓存失效 Outbox Mapper，提供事件写入、到期扫描与乐观锁状态迁移
 * @status : create
 *
 * <p>继续读写历史表 {@code merchant_security_cache_invalidation_outbox}，保证升级后旧的
 * INIT/FAILED 事件仍能由同一中继服务补偿。</p>
 */
public interface MerchantSecurityCacheInvalidationOutboxMapper {

    /**
     * 在业务事务内持久化缓存失效意图，确保提交后发布失败仍可补偿。
     *
     * @param event 初始状态为 INIT、版本为 0 的失效事件
     * @return 写入行数，成功时应为 1
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
    int insertEvent(MerchantSecurityCacheInvalidationOutboxDO event);

    /**
     * 按全局唯一事件号查询当前 Outbox 状态。
     *
     * @param eventId 缓存失效事件唯一标识
     * @return 事件记录；不存在时返回 {@code null}
     */
    @Select("""
            SELECT *
            FROM merchant_security_cache_invalidation_outbox
            WHERE event_id = #{eventId}
            """)
    MerchantSecurityCacheInvalidationOutboxDO selectByEventId(@Param("eventId") String eventId);

    /**
     * 查询到期的 INIT/FAILED 事件，按创建时间稳定排序供定时补偿。
     *
     * @param now 当前补偿时间
     * @param limit 单批最大事件数
     * @return 当前允许重试的失效事件
     */
    @Select("""
            SELECT *
            FROM merchant_security_cache_invalidation_outbox
            WHERE event_status IN ('INIT', 'FAILED')
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<MerchantSecurityCacheInvalidationOutboxDO> selectDueEvents(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * 使用版本号 CAS 将事件推进到 SENT 终态并清理失败上下文。
     *
     * @param id Outbox 主键
     * @param version 调用方读取到的乐观锁版本
     * @param publishedTime 缓存清理成功时间
     * @return 更新行数；0 表示事件已被并发方处理
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
     * 使用版本号 CAS 记录一次发布失败并安排下次重试。
     *
     * @param id Outbox 主键
     * @param version 调用方读取到的乐观锁版本
     * @param nextRetryTime 下一次允许补偿的时间
     * @param failureReason 长度受控且已排除敏感数据的失败原因
     * @param now 本次失败处理时间
     * @return 更新行数；0 表示事件状态或版本已变化
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
