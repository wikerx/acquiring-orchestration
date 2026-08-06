package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxMapper
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易本地消息 Mapper，位于 service-payment 数据访问层，仅负责 transaction_event_outbox 表访问。
 * @status : create
 */
public interface TransactionEventOutboxMapper extends BaseMapper<TransactionEventOutboxDO> {

    /**
     * 写入交易本地消息逻辑表。
     *
     * @param eventDO 交易本地消息记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_event_outbox
            (
              event_no, aggregate_type, aggregate_no, transaction_id, operation_id,
              merchant_id, merchant_order_no, transaction_type, event_type, event_status,
              topic, tag, message_key, message_group, payload_json, retry_count, max_retry_count,
              next_retry_time, sent_time, fail_reason, event_time, transaction_date_time,
              transaction_utc_time, transaction_time_zone, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{eventDO.eventNo}, #{eventDO.aggregateType}, #{eventDO.aggregateNo},
              #{eventDO.transactionId}, #{eventDO.operationId}, #{eventDO.merchantId},
              #{eventDO.merchantOrderNo}, #{eventDO.transactionType}, #{eventDO.eventType},
              #{eventDO.eventStatus}, #{eventDO.topic}, #{eventDO.tag}, #{eventDO.messageKey},
              #{eventDO.messageGroup}, #{eventDO.payloadJson}, #{eventDO.retryCount}, #{eventDO.maxRetryCount},
              #{eventDO.nextRetryTime}, #{eventDO.sentTime}, #{eventDO.failReason},
              #{eventDO.eventTime}, #{eventDO.transactionDateTime}, #{eventDO.transactionUtcTime},
              #{eventDO.transactionTimeZone}, #{eventDO.version}, #{eventDO.deleted},
              #{eventDO.createTime}, #{eventDO.updateTime}
            )
            """)
    int insertLogical(@Param("eventDO") TransactionEventOutboxDO eventDO);

    /**
     * 在单季度半开范围内查询待投递事件。
     *
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @param now 当前时间
     * @param limit 最大返回条数
     * @return 待投递事件列表
     */
    @Select("""
            SELECT *
            FROM transaction_event_outbox
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
              AND event_status IN ('INIT', 'FAILED')
              AND retry_count < max_retry_count
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY transaction_date_time ASC, event_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<TransactionEventOutboxDO> selectDueForPublishLogical(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 使用分片时间和版本 CAS 标记逻辑表事件已投递。
     *
     * @param id 主键 ID
     * @param transactionDateTime 交易分片时间
     * @param version 当前版本号
     * @param sentTime 投递成功时间
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_event_outbox
            SET event_status = 'SENT',
                sent_time = #{sentTime},
                fail_reason = NULL,
                version = version + 1,
                update_time = #{sentTime}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{version}
              AND deleted = 0
              AND event_status IN ('INIT', 'FAILED')
            """)
    int markSentLogical(@Param("id") Long id,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("version") Integer version,
                        @Param("sentTime") LocalDateTime sentTime);

    /**
     * 使用分片时间和版本 CAS 标记逻辑表事件失败。
     *
     * @param id 主键 ID
     * @param transactionDateTime 交易分片时间
     * @param version 当前版本号
     * @param nextRetryTime 下次重试时间
     * @param failReason 失败原因摘要
     * @param now 当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_event_outbox
            SET event_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                fail_reason = #{failReason},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{version}
              AND deleted = 0
              AND event_status IN ('INIT', 'FAILED')
              AND retry_count < max_retry_count
            """)
    int markFailedLogical(@Param("id") Long id,
                          @Param("transactionDateTime") LocalDateTime transactionDateTime,
                          @Param("version") Integer version,
                          @Param("nextRetryTime") LocalDateTime nextRetryTime,
                          @Param("failReason") String failReason,
                          @Param("now") LocalDateTime now);
}
