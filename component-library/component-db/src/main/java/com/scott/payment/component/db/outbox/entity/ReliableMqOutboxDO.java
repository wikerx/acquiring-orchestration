package com.scott.payment.component.db.outbox.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxDO
 * @date : 2026-08-02 22:10
 * @email : scott_x@163.com
 * @description : 非交易可靠 MQ Outbox 持久化对象，保存已脱敏消息快照及 CAS 投递状态
 * @status : create
 */
@Data
public class ReliableMqOutboxDO {

    /** 主键。 */
    private Long id;
    /** 消息唯一编号。 */
    private String eventId;
    /** RocketMQ Topic。 */
    private String topic;
    /** RocketMQ Tag，可为空。 */
    private String tag;
    /** 生产服务编码。 */
    private String producerService;
    /** 链路追踪号。 */
    private String traceId;
    /** 已脱敏 JSON 消息快照。 */
    private String payloadJson;
    /** INIT、PROCESSING、RETRY_WAIT、SENT 或 CLOSED。 */
    private String eventStatus;
    /** 已失败重试次数。 */
    private Integer retryCount;
    /** 最大失败重试次数。 */
    private Integer maxRetryCount;
    /** 下次允许重试时间。 */
    private LocalDateTime nextRetryTime;
    /** 当前实例开始投递时间。 */
    private LocalDateTime processingStartedTime;
    /** 投递成功时间。 */
    private LocalDateTime sentTime;
    /** 最近失败原因摘要。 */
    private String failureReason;
    /** CAS 版本号。 */
    private Integer version;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
