package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.RiskCacheInvalidationOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskCacheInvalidationOutboxMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控规则缓存失效事件数据访问接口。
 * @status : create
 */
public interface RiskCacheInvalidationOutboxMapper {

    /**
     * 在当前业务事务中持久化缓存失效意图。
     *
     * @param event 失效事件
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO risk_cache_invalidation_outbox (
                event_id, namespace, publication_token, generation, event_status,
                retry_count, next_retry_time, published_time,
                failure_reason, version, create_time, update_time
            ) VALUES (
                #{eventId}, #{namespace}, #{publicationToken}, #{generation}, #{eventStatus},
                #{retryCount}, #{nextRetryTime}, #{publishedTime},
                #{failureReason}, #{version}, #{createTime}, #{updateTime}
            )
            """)
    int insertEvent(RiskCacheInvalidationOutboxDO event);

    /**
     * 按唯一事件号查询失效意图。
     *
     * @param eventId 事件号
     * @return 失效事件
     */
    @Select("""
            SELECT *
            FROM risk_cache_invalidation_outbox
            WHERE event_id = #{eventId}
            """)
    RiskCacheInvalidationOutboxDO selectByEventId(@Param("eventId") String eventId);

    /**
     * 查询已到重试时间的失效事件。
     *
     * @param now 当前时间
     * @param limit 最大返回条数
     * @return 待发布事件
     */
    @Select("""
            SELECT *
            FROM risk_cache_invalidation_outbox
            WHERE event_status IN ('INIT', 'FAILED')
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<RiskCacheInvalidationOutboxDO> selectDueEvents(@Param("now") LocalDateTime now,
                                                        @Param("limit") int limit);

    /**
     * 门禁过期后以 CAS 方式保存新发布凭证。
     *
     * @param id 记录主键
     * @param version 当前版本
     * @param publicationToken 新门禁 token
     * @param generation 新代际
     * @param now 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_cache_invalidation_outbox
            SET publication_token = #{publicationToken},
                generation = #{generation},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND event_status IN ('INIT', 'FAILED')
            """)
    int replacePublication(@Param("id") Long id,
                           @Param("version") Integer version,
                           @Param("publicationToken") String publicationToken,
                           @Param("generation") String generation,
                           @Param("now") LocalDateTime now);

    /**
     * 以 CAS 方式标记发布成功。
     *
     * @param id 记录主键
     * @param version 当前版本
     * @param publishedTime 发布时间
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_cache_invalidation_outbox
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
     * 以 CAS 方式记录失败并安排下次重试。
     *
     * @param id 记录主键
     * @param version 当前版本
     * @param nextRetryTime 下次重试时间
     * @param failureReason 失败摘要
     * @param now 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE risk_cache_invalidation_outbox
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
