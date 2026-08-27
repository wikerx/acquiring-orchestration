package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionEventOutboxDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 清分服务写入交易 Outbox 的持久化实体，完成事件使用顺序模式，失败重试使用 Broker 定时模式。
 * @status : create
 */
@Data
@TableName("transaction_event_outbox")
public class ClearingTransactionEventOutboxDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private String aggregateType;
    private String aggregateNo;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String merchantOrderNo;
    private String transactionType;
    private String eventType;
    private String eventStatus;
    private String topic;
    private String tag;
    private String messageKey;
    private String messageGroup;
    private String deliveryMode;
    private LocalDateTime deliverAt;
    private String payloadJson;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime eventTime;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
