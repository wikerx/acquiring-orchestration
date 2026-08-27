package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxDO
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易本地消息表实体，位于 service-payment 持久化层，用于把本地交易结果与 RocketMQ 投递解耦。
 * @status : create
 */
@Data
@TableName("transaction_event_outbox")
public class TransactionEventOutboxDO implements Serializable {

    /**
     * 序列化版本号，用于事件补偿、重试和测试场景的对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据库主键 ID，正式表使用系统统一主键规则。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 本地事件编号，建议与当前交易动作单号保持一致或使用全局事件号。
     */
    private String eventNo;

    /**
     * 聚合类型，例如 PAYMENT_TRANSACTION。
     */
    private String aggregateType;

    /**
     * 聚合标识，当前使用内部生命周期 operation_id。
     */
    private String aggregateNo;

    /**
     * 平台当前交易唯一标识，对应 transaction_operation.transaction_id。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识，对应 transaction_order.operation_id。
     */
    private String operationId;

    /**
     * 商户号，用于事件排查、补偿和下游消费幂等。
     */
    private String merchantId;

    /**
     * 商户订单号，用于商户侧查询和补偿排查。
     */
    private String merchantOrderNo;

    /**
     * 交易类型，对齐系统字典 transaction_type。
     */
    private String transactionType;

    /**
     * 事件类型，例如 TRANSACTION_CREATED、TRANSACTION_STATUS_CHANGED。
     */
    private String eventType;

    /**
     * 事件状态，INIT 表示待投递，SENT 表示已投递，FAILED 表示投递失败待补偿。
     */
    private String eventStatus;

    /**
     * RocketMQ Topic。
     */
    private String topic;

    /**
     * RocketMQ Tag。
     */
    private String tag;

    /**
     * 消息业务键，用于 MQ 消费幂等和日志追踪。
     */
    private String messageKey;

    /**
     * 顺序消息分组键，如 transaction_id。
     */
    private String messageGroup;

    /**
     * 投递模式：AUTO、NORMAL、ORDERLY 或 SCHEDULED；历史记录默认 AUTO。
     */
    private String deliveryMode = "AUTO";

    /**
     * SCHEDULED 消息的 Broker 最早投递 UTC 时间；其它模式为空，数据库使用 DATETIME(3)。
     */
    private LocalDateTime deliverAt;

    /**
     * 消息体 JSON，禁止保存完整卡号、CVV、JWT、私钥或 API Key。
     */
    private String payloadJson;

    /**
     * 事件产生时间，参与季度分表路由，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime eventTime;

    /**
     * 交易业务时间，便于按交易维度检索本地事件。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间，用于跨时区排序和审计。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时间所属 IANA 时区，默认 Asia/Shanghai。
     */
    private String transactionTimeZone;

    /**
     * 当前重试次数。
     */
    private Integer retryCount;

    /**
     * 最大重试次数，超过后需要人工介入或补偿任务处理。
     */
    private Integer maxRetryCount;

    /**
     * 下次投递时间。
     */
    private LocalDateTime nextRetryTime;

    /**
     * 投递成功时间。
     */
    private LocalDateTime sentTime;

    /**
     * 最近一次投递失败原因。
     */
    private String failReason;

    /**
     * 乐观锁版本号，用于补偿投递状态 CAS 更新。
     */
    private Integer version;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Integer deleted;

    /**
     * 记录创建时间，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime updateTime;

}
