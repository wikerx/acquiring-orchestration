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
    /** Outbox 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 确定性事件号，数据库唯一键承担最终幂等。 */
    private String eventNo;
    /** 聚合类型。 */
    private String aggregateType;
    /** 聚合业务号。 */
    private String aggregateNo;
    /** 关联动作交易号。 */
    private String transactionId;
    /** 关联动作操作号。 */
    private String operationId;
    /** 平台商户号。 */
    private String merchantId;
    /** 商户订单号，仅用于事件追踪。 */
    private String merchantOrderNo;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 事件类型稳定编码。 */
    private String eventType;
    /** Outbox 发布状态。 */
    private String eventStatus;
    /** RocketMQ 主题。 */
    private String topic;
    /** RocketMQ 标签。 */
    private String tag;
    /** RocketMQ 消息键，用于追踪和 Broker 去重辅助。 */
    private String messageKey;
    /** 顺序消息分组键；定时重试消息允许为空。 */
    private String messageGroup;
    /** 投递模式：顺序或 Broker 定时。 */
    private String deliveryMode;
    /** Broker 绝对投递 UTC 时间；非定时消息为空。 */
    private LocalDateTime deliverAt;
    /** 非敏感事件 JSON，不得包含卡号、密钥或认证原文。 */
    private String payloadJson;
    /** Outbox 技术发布重试次数。 */
    private Integer retryCount;
    /** Outbox 技术发布最大重试次数。 */
    private Integer maxRetryCount;
    /** 下一次技术重试 UTC 时间。 */
    private LocalDateTime nextRetryTime;
    /** 业务事件发生 UTC 时间。 */
    private LocalDateTime eventTime;
    /** 关联动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 关联动作 UTC 业务时间。 */
    private LocalDateTime transactionUtcTime;
    /** 关联动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** Outbox 状态 CAS 版本。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
