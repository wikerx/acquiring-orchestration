package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationRetryOutboxMapper
 * @date : 2026-08-06 12:36
 * @email : scott_x@163.com
 * @description : 自动商户通知重试事件 Mapper，只负责向同分片 transaction_event_outbox 写入消息意图
 * @status : create
 */
public interface DataMerchantNotificationRetryOutboxMapper {

    /**
     * 写入与通知任务相同季度的交易 Outbox。
     *
     * @param eventDO 自动重试事件快照
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_event_outbox
            (
              event_no, aggregate_type, aggregate_no, transaction_id, operation_id,
              merchant_id, merchant_order_no, transaction_type, event_type, event_status,
              topic, tag, message_key, message_group, payload_json, retry_count, max_retry_count,
              next_retry_time, event_time, transaction_date_time, transaction_utc_time,
              transaction_time_zone, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{event.eventNo}, #{event.aggregateType}, #{event.aggregateNo}, #{event.transactionId},
              #{event.operationId}, #{event.merchantId}, #{event.merchantOrderNo},
              #{event.transactionType}, #{event.eventType}, #{event.eventStatus}, #{event.topic},
              #{event.tag}, #{event.messageKey}, #{event.messageGroup}, #{event.payloadJson},
              #{event.retryCount}, #{event.maxRetryCount}, #{event.nextRetryTime}, #{event.eventTime},
              #{event.transactionDateTime}, #{event.transactionUtcTime}, #{event.transactionTimeZone},
              #{event.version}, #{event.deleted}, #{event.createTime}, #{event.updateTime}
            )
            """)
    int insert(@Param("event") DataMerchantNotificationRetryOutboxDO eventDO);
}
