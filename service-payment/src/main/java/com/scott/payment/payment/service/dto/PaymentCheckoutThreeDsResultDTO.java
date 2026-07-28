package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Hosted Checkout 3DS 认证结果。
 */
@Data
public class PaymentCheckoutThreeDsResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;
    private String authenticationTransactionId;
    private String channelOrderNo;
    private String channelTransactionId;
    private String channelRequestId;
    private Long channelMidConfigId;
    private String threeDsStatus;
    private String threeDsVersion;
    private String threeDsTransactionId;
    private String threeDsServerTransactionId;
    private String acsTransactionId;
    private String dsTransactionId;
    private String eci;
    private String cavv;
    private String redirectHtml;
    private String redirectUrl;
    private String failureCode;
    private String failureMessage;
    private String rawResponseMasked;

    public boolean passed() {
        return "PASSED".equals(status);
    }

    public boolean challengeRequired() {
        return "CHALLENGE_REQUIRED".equals(status);
    }

    public boolean failed() {
        return "FAILED".equals(status);
    }

    public boolean processing() {
        return "PROCESSING".equals(status);
    }
}
