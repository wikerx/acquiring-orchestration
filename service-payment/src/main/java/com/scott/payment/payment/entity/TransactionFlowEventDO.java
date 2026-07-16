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
 * @classname : TransactionFlowEventDO
 * @date : 2026-07-14 19:34
 * @email : scott_x@163.com
 * @description : 交易流程事件实体，位于 service-payment 持久化层，记录 API、风控、路由、渠道、回调、MQ 等关键节点，供后台时间线展示。
 * @status : create
 */
@Data
@TableName("transaction_flow_event")
public class TransactionFlowEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String flowEventId;

    private String transactionId;

    private String operationId;

    private String eventType;

    private String eventStage;

    private String eventStatus;

    private String eventName;

    private String eventContent;

    private String previousStatus;

    private String currentStatus;

    private String operatorType;

    private String operatorId;

    private String referenceType;

    private String referenceId;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime eventTime;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private LocalDateTime createTime;
}
