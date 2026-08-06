package com.scott.payment.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationRetryOutboxDO
 * @date : 2026-08-06 12:36
 * @email : scott_x@163.com
 * @description : service-data 写入 transaction_event_outbox 的自动商户通知重试事件快照
 * @status : create
 */
@Data
public class DataMerchantNotificationRetryOutboxDO {

    /** 幂等事件编号。 */
    private String eventNo;
    /** 事件聚合类型。 */
    private String aggregateType;
    /** 通知任务聚合编号。 */
    private String aggregateNo;
    /** 平台交易号。 */
    private String transactionId;
    /** 平台交易生命周期号。 */
    private String operationId;
    /** 商户号。 */
    private String merchantId;
    /** 商户订单号。 */
    private String merchantOrderNo;
    /** 交易事件分类。 */
    private String transactionType;
    /** 自动重试事件类型。 */
    private String eventType;
    /** INIT、FAILED 或 SENT。 */
    private String eventStatus;
    /** RocketMQ Topic。 */
    private String topic;
    /** RocketMQ Tag。 */
    private String tag;
    /** MQ 业务消息键。 */
    private String messageKey;
    /** MQ 顺序分组键。 */
    private String messageGroup;
    /** 不含商户协议数据的内部事件 JSON。 */
    private String payloadJson;
    /** Outbox 发布失败次数。 */
    private Integer retryCount;
    /** Outbox 最大发布次数。 */
    private Integer maxRetryCount;
    /** Relay 下次发布时间；首次为空表示立即发布到延迟 MQ。 */
    private LocalDateTime nextRetryTime;
    /** 事件产生时间。 */
    private LocalDateTime eventTime;
    /** 交易分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 交易业务时间对应 UTC 时间。 */
    private LocalDateTime transactionUtcTime;
    /** 交易业务时区。 */
    private String transactionTimeZone;
    /** CAS 版本。 */
    private Integer version;
    /** 软删除标识。 */
    private Integer deleted;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
