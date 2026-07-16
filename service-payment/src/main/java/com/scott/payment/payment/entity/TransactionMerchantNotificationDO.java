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
 * @classname : TransactionMerchantNotificationDO
 * @date : 2026-07-14 19:38
 * @email : scott_x@163.com
 * @description : 商户通知任务实体，位于 service-payment 持久化层，保存交易结果通知商户的任务状态、配置快照和重试计划。
 * @status : create
 */
@Data
@TableName("transaction_merchant_notification")
public class TransactionMerchantNotificationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notifyId;

    private String transactionId;

    private String operationId;

    private String merchantId;

    private String merchantOrderNo;

    private String notifyType;

    private String eventType;

    private String notifyStatus;

    private String notifyConfigVersion;

    private String notifyConfigSnapshotJson;

    private String targetUrlHash;

    private String targetUrlMasked;

    private String payloadJsonMasked;

    private String signType;

    private Integer lastAttemptNo;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryTime;

    private LocalDateTime successTime;

    private String failReason;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private Integer version;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
