package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementEventOutboxDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算服务本地事件 Outbox；资金事务内保存冻结 JSON，事务外按 operationId 消息组至少一次发布到交易级 FIFO Topic。
 * @status : create
 */
@Data
public class SettlementEventOutboxDO {
    /** Outbox 数据库主键，插入前允许为空。 */
    private Long id;
    /** 全局事件号，数据库必须唯一。 */
    private String eventNo;
    /** 产生事件的正式结算批次号。 */
    private String settlementBatchNo;
    /** 来源真实交易候选主键；批次级事件允许为空。 */
    private Long candidateId;
    /** RocketMQ 目标 Topic，不允许为空。 */
    private String topic;
    /** 事件业务 Tag，用于消费者路由。 */
    private String tag;
    /** MQ 消息唯一键，用于追踪和消费者幂等。 */
    private String messageKey;
    /** 顺序消息组键，交易事件使用 operationId 保证同动作有序。 */
    private String messageGroup;
    /** 资金事务内冻结的非敏感 JSON 载荷，不在发送阶段重新查询拼装。 */
    private String payloadJson;
    /** PENDING、PROCESSING、SENT 或待重试状态。 */
    private String eventStatus;
    /** 已失败发送次数，不包含成功发送。 */
    private Integer retryCount;
    /** 下次允许认领时间；首次发送允许为空。 */
    private LocalDateTime nextRetryTime;
    /** 当前短租约执行者；未认领时允许为空。 */
    private String processingOwner;
    /** 当前发送租约截止时间；未认领时允许为空。 */
    private LocalDateTime processingDeadline;
    /** MQ 确认发送成功时间；未发送时允许为空。 */
    private LocalDateTime sentTime;
    /** 最近一次脱敏失败码；成功或未失败时允许为空。 */
    private String lastFailureCode;
    /** Outbox 状态 CAS 版本，不允许为空。 */
    private Long version;
    /** 事件创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 事件状态最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
