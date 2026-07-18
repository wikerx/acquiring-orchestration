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
 * @classname : TransactionMerchantNotificationLogDO
 * @date : 2026-07-14 22:22
 * @email : scott_x@163.com
 * @description : 商户通知请求日志实体，位于 service-payment 持久化层，保存每一次通知商户的脱敏请求、响应、耗时和成功标识。
 * @status : create
 */
@Data
@TableName("transaction_merchant_notification_log")
public class TransactionMerchantNotificationLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notifyLogId;

    private String notifyId;

    private String transactionId;

    private String operationId;

    private String merchantId;

    private Integer attemptNo;

    private String targetUrlHash;

    private Integer httpStatus;

    private String requestHeaderJsonMasked;

    private String requestBodyJsonMasked;

    private String responseBodyJsonMasked;

    private Integer success;

    private String errorMessage;

    private LocalDateTime notifyTime;

    private Integer durationMillis;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private LocalDateTime createTime;
}
