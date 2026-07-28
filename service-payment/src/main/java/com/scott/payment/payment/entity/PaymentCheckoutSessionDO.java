package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 会话主表实体。
 */
@Data
@TableName("payment_checkout_session")
public class PaymentCheckoutSessionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checkoutSessionId;
    private String merchantId;
    private String merchantOrderNo;
    private String merchantRequestId;
    private String requestFingerprint;
    private String paymentAction;
    private String integrationType;
    private String checkoutStatus;
    private String processStage;
    private LocalDateTime lastStatusTime;
    private String operationId;
    private String rootTransactionId;
    private String latestTransactionId;
    private LocalDateTime transactionDateTime;
    private String labelCurrency;
    private BigDecimal labelAmount;
    private Integer currencyExponent;
    private String orderSubject;
    private String orderDescription;
    private String orderItemsJson;
    private String allowedPaymentMethodsJson;
    private String selectedPaymentMethod;
    private String selectedPaymentBrand;
    private String channelCode;
    private Long channelMidConfigId;
    private String merchantDisplayName;
    private String merchantLogoUrl;
    private String merchantReturnUrl;
    private String merchantCancelUrl;
    private String merchantNotifyUrlHash;
    private String locale;
    private String payerCountry;
    private String payerEmailMasked;
    private String payerEmailHash;
    private Integer retryAllowed;
    private Integer maxAttemptCount;
    private Integer attemptCount;
    private String successAttemptId;
    private String lastAttemptId;
    private String checkoutDomain;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private LocalDateTime cancelTime;
    private LocalDateTime blockedTime;
    private String blockReasonCode;
    private LocalDateTime lastOpenTime;
    private LocalDateTime lastSubmitTime;
    private LocalDateTime nextChannelMatchTime;
    private String resultSnapshot;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
