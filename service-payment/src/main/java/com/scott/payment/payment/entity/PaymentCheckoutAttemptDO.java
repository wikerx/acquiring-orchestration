package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 支付尝试实体。
 */
@Data
@TableName("payment_checkout_attempt")
public class PaymentCheckoutAttemptDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checkoutAttemptId;
    private String checkoutSessionId;
    private String merchantId;
    private String merchantOrderNo;
    private Integer attemptNo;
    private String attemptRequestId;
    private String requestFingerprint;
    private String attemptStatus;
    private String processStage;
    private String paymentMethod;
    private String paymentBrand;
    private String labelCurrency;
    private BigDecimal labelAmount;
    private String channelRequestCurrency;
    private BigDecimal channelRequestAmount;
    private String operationId;
    private String transactionId;
    private LocalDateTime transactionDateTime;
    private String channelCode;
    private Long channelMidConfigId;
    private String channelOrderNo;
    private String channelTransactionId;
    private String channelRequestId;
    private String channelStatus;
    private String channelResponseCode;
    private String channelResponseMessage;
    private String cardBin;
    private String cardLast4;
    private String cardNumberMasked;
    private String cardholderNameMasked;
    private String paymentAccountHash;
    private Integer threeDsRequired;
    private String threeDsStatus;
    private String threeDsVersion;
    private String threeDsTransactionId;
    private String threeDsServerTransactionId;
    private String acsTransactionId;
    private String dsTransactionId;
    private String eci;
    private Integer liabilityShift;
    private String threeDsReturnTokenHash;
    private String authenticationRedirectUrlHash;
    private String browserInfoJson;
    private String deviceInfoJson;
    private String failureReasonCode;
    private String failureReasonMessage;
    private String payerVisibleMessage;
    private LocalDateTime submitTime;
    private LocalDateTime authenticationStartTime;
    private LocalDateTime authenticationCompleteTime;
    private LocalDateTime channelSubmitTime;
    private LocalDateTime completeTime;
    private String resultSnapshot;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
