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
 * @classname : TransactionChannelCallbackDO
 * @date : 2026-07-14 22:20
 * @email : scott_x@163.com
 * @description : 渠道回调业务记录实体，位于 service-payment 持久化层，保存回调幂等键、解析结果和状态推进结果，避免重复回调重复处理资金状态。
 * @status : create
 */
@Data
@TableName("transaction_channel_callback")
public class TransactionChannelCallbackDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String callbackId;

    private String callbackLogId;

    private String transactionId;

    private String operationId;

    private String channelCode;

    private String channelOrderNo;

    private String channelTransactionId;

    private String callbackType;

    private String channelEventType;

    private String callbackStatus;

    private String idempotencyKey;

    private Integer signatureValid;

    private Integer ipAllowed;

    private String parsedTransactionStatus;

    private String previousTransactionStatus;

    private String targetTransactionStatus;

    private String processResult;

    private String failReason;

    private LocalDateTime callbackReceivedTime;

    private LocalDateTime processedTime;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private Integer version;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
