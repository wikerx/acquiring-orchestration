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
 * @classname : TransactionCallbackInboxDO
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 实体，位于 service-payment 持久化层，在返回渠道 ACK 前持久化已验证标准事件并记录异步消费状态。
 * @status : create
 */
@Data
@TableName("transaction_callback_inbox")
public class TransactionCallbackInboxDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增物理主键，不作为外部幂等依据。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局 Inbox 业务号，用于日志关联和人工补偿定位。 */
    private String inboxId;

    /** 首次接收回调时的链路号，Worker 重放时恢复到线程上下文。 */
    private String traceId;

    /** 标准化渠道编码。 */
    private String channelCode;

    /** 验签凭据标识，例如 MID；禁止保存密钥。 */
    private String credentialHint;

    /** 渠道稳定事件号，用于区分同一交易的不同回调事件。 */
    private String callbackEventId;

    /** 渠道、凭据标识和事件号生成的 SHA-256 数据库幂等键。 */
    private String idempotencyKey;

    /** 已验签标准事件 JSON，不包含原始请求头或可逆报文正文。 */
    private String payloadJson;

    /** 数量和长度受控的脱敏请求头摘要 JSON。 */
    private String requestHeaderJsonMasked;

    /** 原始回调报文的精确字节数。 */
    private Integer requestBodyLength;

    /** 原始回调报文的 SHA-256 十六进制摘要。 */
    private String requestBodySha256;

    /** Inbox 消费状态，由 TransactionCallbackInboxStatusEnum 约束。 */
    private String inboxStatus;

    /** 已记录的失败或超时恢复次数。 */
    private Integer retryCount;

    /** 允许的最大失败次数，达到后进入 CLOSED。 */
    private Integer maxRetryCount;

    /** 下一次允许被 Worker 抢占的业务时间。 */
    private LocalDateTime nextRetryTime;

    /** 最近失败异常类型，不保存异常消息或敏感数据。 */
    private String lastError;

    /** OpenAPI 入口首次收到回调的业务时间。 */
    private LocalDateTime receivedTime;

    /** 交易回调业务处理完成时间。 */
    private LocalDateTime processedTime;

    /** 多实例抢占与结果回写使用的 CAS 版本号。 */
    private Integer version;

    /** Inbox 记录创建时间。 */
    private LocalDateTime createTime;

    /** 状态更新时间，同时作为 PROCESSING 租约起点。 */
    private LocalDateTime updateTime;
}
