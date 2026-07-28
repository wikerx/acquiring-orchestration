package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MPGS 3DS Direct API 认证请求。
 */
@Data
public class MpgsThreeDsAuthenticationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String operationId;
    private String transactionId;
    private String channelOrderNo;
    private String authenticationTransactionId;
    private String merchantId;
    private String merchantOrderNo;
    private String merchantOrderId;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionDateTime;
    private String cardNo;
    private String expirationMonth;
    private String expirationYear;
    private String securityCode;
    private String cardBrand;
    private String redirectResponseUrl;
    private String browserInfoJson;
    private ChannelPaymentRequest.BillingInfo billingInfo;
    private Map<String, String> extension = new HashMap<>();
}
