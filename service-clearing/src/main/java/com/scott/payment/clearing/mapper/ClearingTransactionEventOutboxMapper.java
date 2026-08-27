package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionEventOutboxMapper
 * @date : 2026-08-26 10:45
 * @email : scott_x@163.com
 * @description : 清分本地事件 Mapper，只负责把完成或重试事件写入当前动作季度 Outbox，不直接调用 RocketMQ。
 * @status : create
 */
public interface ClearingTransactionEventOutboxMapper {

    /**
     * 写入一条由现有交易 Outbox Relay 可靠投递的事件。
     *
     * @param row 完整的非敏感清分事件
     * @return 1 表示写入成功；唯一键或数据约束冲突直接抛出数据库异常
     */
    @Insert("""
            INSERT INTO transaction_event_outbox
            (event_no, aggregate_type, aggregate_no, transaction_id, operation_id,
             merchant_id, merchant_order_no, transaction_type, event_type, event_status,
             topic, tag, message_key, message_group, delivery_mode, deliver_at,
             payload_json, retry_count, max_retry_count, next_retry_time, event_time,
             transaction_date_time, transaction_utc_time, transaction_time_zone,
             version, deleted, create_time, update_time)
            VALUES
            (#{row.eventNo}, #{row.aggregateType}, #{row.aggregateNo}, #{row.transactionId},
             #{row.operationId}, #{row.merchantId}, #{row.merchantOrderNo}, #{row.transactionType},
             #{row.eventType}, #{row.eventStatus}, #{row.topic}, #{row.tag}, #{row.messageKey},
             #{row.messageGroup}, #{row.deliveryMode}, #{row.deliverAt}, #{row.payloadJson},
             #{row.retryCount}, #{row.maxRetryCount}, #{row.nextRetryTime}, #{row.eventTime},
             #{row.transactionDateTime}, #{row.transactionUtcTime}, #{row.transactionTimeZone},
             #{row.version}, #{row.deleted}, #{row.createTime}, #{row.updateTime})
            """)
    int insertLogical(@Param("row") ClearingTransactionEventOutboxDO row);

    /**
     * 按稳定事件号和季度分片时间锁定已有事件，供调用方核对幂等身份。
     *
     * @param eventNo 稳定事件号
     * @param transactionDateTime 事件所属交易季度分片时间
     * @return 已有事件；不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_event_outbox
            WHERE event_no = #{eventNo}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    ClearingTransactionEventOutboxDO selectByEventNoForUpdate(
            @Param("eventNo") String eventNo,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
