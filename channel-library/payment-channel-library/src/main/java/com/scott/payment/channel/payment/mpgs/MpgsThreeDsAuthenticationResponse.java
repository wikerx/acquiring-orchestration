package com.scott.payment.channel.payment.mpgs;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * MPGS 3DS Direct API 认证响应摘要。
 */
@Data
public class MpgsThreeDsAuthenticationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String operationId;
    private String transactionId;
    private String channelOrderNo;
    private String authenticationTransactionId;
    private String result;
    private String gatewayCode;
    private String gatewayRecommendation;
    private String authenticationStatus;
    private String payerInteraction;
    private String threeDsVersion;
    private String threeDsTransactionId;
    private String threeDsServerTransactionId;
    private String acsTransactionId;
    private String dsTransactionId;
    private String eci;
    private String cavv;
    private String redirectHtml;
    private String redirectUrl;
    private String responseCode;
    private String responseMessage;
    private String rawResponseMasked;
    private Map<String, String> extension = new HashMap<>();
}
