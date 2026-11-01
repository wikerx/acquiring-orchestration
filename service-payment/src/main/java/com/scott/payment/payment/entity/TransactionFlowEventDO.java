package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易流程事件实体，位于 service-payment 持久化层，记录 API、风控、路由、渠道、回调、MQ 等关键节点，供后台时间线展示。
 */
@Data
@TableName("transaction_flow_event")
public class TransactionFlowEventDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


    private String flowEventId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;

    /**
     * {@code eventType}，用于区分 {@code TransactionFlowEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String eventType;

    /**
     * 持久化的{@code eventStage}，用于还原当前记录的业务事实。
     */
    private String eventStage;


    private String eventStatus;


    private String eventName;

    /**
     * 持久化的{@code eventContent}，用于还原当前记录的业务事实。
     */
    private String eventContent;


    private String previousStatus;


    private String currentStatus;

    /**
     * {@code operatorType}，用于区分 {@code TransactionFlowEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String operatorType;

    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     */
    private String operatorId;

    /**
     * {@code referenceType}，用于区分 {@code TransactionFlowEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String referenceType;


    private String referenceId;


    private String errorCode;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    private String errorMessage;

    /**
     * 处理当前事件的稳定服务名，仅在节点审计迁移完成并显式启用后写入。
     */
    private String processorService;

    /**
     * 部署平台注入的服务实例或 Pod 标识，不使用运行时随机值。
     */
    private String processorInstanceId;

    /**
     * 事件处理时的主机名快照，仅供内部排障。
     */
    private String processorHostName;

    /**
     * 部署平台显式注入的节点内网 IP，不通过本机多网卡自动推断。
     */
    private String processorPrivateIp;

    /**
     * 当前调用链 Trace ID，用于跨服务关联内部日志与流程事件。
     */
    private String traceId;

    /**
     * 持久化的{@code eventTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime eventTime;

    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
