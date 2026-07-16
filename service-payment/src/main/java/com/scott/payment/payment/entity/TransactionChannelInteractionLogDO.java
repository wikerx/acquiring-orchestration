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
 * @classname : TransactionChannelInteractionLogDO
 * @date : 2026-07-14 19:32
 * @email : scott_x@163.com
 * @description : 渠道交互日志实体，位于 service-payment 持久化层，保存渠道请求、响应和异常的脱敏 JSON 审计日志。
 * @status : create
 */
@Data
@TableName("transaction_channel_interaction_log")
public class TransactionChannelInteractionLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物理表自增主键，仅用于数据库内部定位。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道交互日志唯一标识，一次请求响应聚合为一条日志时保持唯一。
     */
    private String interactionLogId;

    /**
     * 平台渠道请求 ID，关联 transaction_channel_request.request_id。
     */
    private String requestId;

    /**
     * 平台当前交易 ID，用于按 transaction_date_time + transaction_id 定位分表数据。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识，用于查看授权、请款、退款、撤销等同一原始交易链路。
     */
    private String operationId;

    /**
     * 渠道编码，例如 MPGS。
     */
    private String channelCode;

    /**
     * 交互类型，当前主要为 REQUEST_RESPONSE，兼容历史 REQUEST、RESPONSE、EXCEPTION。
     */
    private String interactionType;

    /**
     * 调用渠道接口的 HTTP 方法。
     */
    private String httpMethod;

    /**
     * 脱敏后的渠道请求 URL，不包含明文敏感凭据。
     */
    private String requestUrlMasked;

    /**
     * 渠道 HTTP 状态码；渠道客户端未直接暴露时允许为空。
     */
    private Integer httpStatus;

    /**
     * 脱敏后的渠道请求头 JSON，禁止保存 Authorization、API Key 等完整敏感值。
     */
    private String requestHeaderJsonMasked;

    /**
     * 脱敏后的渠道请求体 JSON，卡号、CVV、密码等敏感字段必须脱敏。
     */
    private String requestBodyJsonMasked;

    /**
     * 脱敏后的渠道响应头 JSON。
     */
    private String responseHeaderJsonMasked;

    /**
     * 脱敏后的渠道响应体 JSON，后台用于排查真实渠道响应码和失败原因。
     */
    private String responseBodyJsonMasked;

    /**
     * 调用渠道发生异常时的异常类型；正常收到渠道响应时为空。
     */
    private String exceptionType;

    /**
     * 调用渠道发生异常时的异常摘要，禁止包含完整敏感报文。
     */
    private String exceptionMessage;

    /**
     * 渠道交互耗时，单位毫秒。
     */
    private Integer durationMillis;

    /**
     * 链路追踪 ID，可为空。
     */
    private String traceId;

    /**
     * 渠道交互发生时间，使用 DATETIME(3) 保留毫秒。
     */
    private LocalDateTime interactionTime;

    /**
     * 交易业务时间，所有 transaction_* 分表统一使用该字段路由。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应的 UTC 时间，用于跨时区查询和展示换算。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区，例如 Asia/Shanghai。
     */
    private String transactionTimeZone;

    /**
     * 日志创建时间。
     */
    private LocalDateTime createTime;
}
