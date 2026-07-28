package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 安全事件实体。
 */
@Data
@TableName("payment_checkout_security_event")
public class PaymentCheckoutSecurityEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String securityEventId;
    private String checkoutSessionId;
    private String checkoutAttemptId;
    private String merchantId;
    private String tokenHash;
    private String securityEventType;
    private String securityDecision;
    private String blockReasonCode;
    private Integer httpStatus;
    private String requestMethod;
    private String requestPathHash;
    private String clientIpHash;
    private String clientIpCountry;
    private String userAgentHash;
    private String deviceIdHash;
    private String originHash;
    private String refererHash;
    private BigDecimal riskScore;
    private String evidenceJson;
    private String traceId;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
